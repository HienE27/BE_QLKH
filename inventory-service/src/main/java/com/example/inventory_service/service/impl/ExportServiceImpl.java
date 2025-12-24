package com.example.inventory_service.service.impl;

import com.example.inventory_service.dto.ExportDetailDto;
import com.example.inventory_service.dto.ExportDetailRequest;
import com.example.inventory_service.dto.SupplierExportDto;
import com.example.inventory_service.dto.SupplierExportRequest;
import com.example.inventory_service.entity.ExportStatus;
import com.example.inventory_service.entity.ExportType;
import com.example.inventory_service.entity.ShopExport;
import com.example.inventory_service.entity.ShopExportDetail;
import com.example.inventory_service.exception.NotFoundException;
import com.example.inventory_service.entity.ShopStock;
import com.example.inventory_service.repository.ShopExportDetailRepository;
import com.example.inventory_service.repository.ShopExportRepository;
import com.example.inventory_service.repository.ShopStockRepository;
import com.example.inventory_service.service.ExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExportServiceImpl implements ExportService {

    private static final Logger logger = LoggerFactory.getLogger(ExportServiceImpl.class);

    private final ShopExportRepository exportRepo;
    private final ShopExportDetailRepository detailRepo;
    private final com.example.inventory_service.repository.ShopStoreRepository storeRepo;
    private final ShopStockRepository stockRepo;
    private com.example.inventory_service.repository.UserQueryRepository userRepo;
    private final com.example.inventory_service.client.AiServiceClient aiServiceClient;

    public ExportServiceImpl(
            ShopExportRepository exportRepo,
            ShopExportDetailRepository detailRepo,
            com.example.inventory_service.repository.ShopStoreRepository storeRepo,
            ShopStockRepository stockRepo,
            com.example.inventory_service.repository.UserQueryRepository userRepo,
            com.example.inventory_service.client.AiServiceClient aiServiceClient) {
        this.exportRepo = exportRepo;
        this.detailRepo = detailRepo;
        this.storeRepo = storeRepo;
        this.stockRepo = stockRepo;
        this.userRepo = userRepo;
        this.aiServiceClient = aiServiceClient;
    }

    @Override
    @Transactional
    public SupplierExportDto create(SupplierExportRequest req) {
        // Validation: Phiếu xuất bắt buộc phải có kho và khách hàng
        // Lấy storeId từ header hoặc từ item đầu tiên
        Long storeId = req.getStoreId();
        if (storeId == null && req.getItems() != null && !req.getItems().isEmpty()) {
            // Nếu header không có storeId, lấy từ item đầu tiên
            ExportDetailRequest firstItem = req.getItems().get(0);
            if (firstItem.getStoreId() != null) {
                storeId = firstItem.getStoreId();
            }
        }
        if (storeId == null) {
            throw new IllegalArgumentException("Phiếu xuất kho bắt buộc phải có kho xuất");
        }
        if (req.getCustomerId() == null &&
                (req.getCustomerName() == null || req.getCustomerName().isBlank())) {
            throw new IllegalArgumentException("Phiếu xuất kho bắt buộc phải có thông tin khách hàng");
        }

        LocalDateTime now = LocalDateTime.now();

        ShopExport export = new ShopExport();
        export.setCode(req.getCode() != null ? req.getCode() : "PXNCC" + System.currentTimeMillis());
        export.setExportType(ExportType.ORDER); // Cố định = ORDER
        export.setStoreId(storeId);

        export.setNote(req.getNote());
        export.setDescription(req.getDescription());
        export.setCustomerId(req.getCustomerId());
        // Lưu thông tin khách hàng nếu có (nhập trực tiếp)
        export.setCustomerName(req.getCustomerName());
        export.setCustomerPhone(req.getCustomerPhone());
        export.setCustomerAddress(req.getCustomerAddress());
        export.setStatus(ExportStatus.PENDING);
        export.setExportsDate(now);
        export.setUserId(null);
        export.setOrderId(req.getOrderId());
        export.setCreatedAt(now);
        export.setUpdatedAt(now);
        
        // Set createdBy từ userId nếu có
        Long currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            export.setCreatedBy(currentUserId);
        } else if (export.getUserId() != null) {
            export.setCreatedBy(export.getUserId());
        }

        // Lưu ảnh
        if (req.getAttachmentImages() != null && !req.getAttachmentImages().isEmpty()) {
            String joined = req.getAttachmentImages().stream()
                    .map(this::normalizeImagePath)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(";"));
            export.setAttachmentImage(joined);
        } else {
            export.setAttachmentImage(null);
        }

        export = exportRepo.save(export);

        // Ghi nhật ký hoạt động: tạo phiếu xuất
        try {
            Long uid = currentUserId;
            String uname = getCurrentUsername();
            if (uname == null) uname = "system";
            System.out.println("Preparing to send activity log: action=CREATE_DELIVERY, resourceType=EXPORT, resourceId=" + export.getId() + ", userId=" + uid + ", username=" + uname + ", code=" + export.getCode());
            sendActivityLog(uid, uname, "CREATE_DELIVERY", "EXPORT", export.getId(), export.getCode(), "Created export: " + export.getCode());
        } catch (Exception e) {
            logger.warn("Failed to send activity log for export create: {}", e.getMessage());
        }

        // Chi tiết phiếu xuất
        BigDecimal total = BigDecimal.ZERO;
        List<ShopExportDetail> details = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<com.example.inventory_service.dto.UpdateReceiptMetadataRequest.ProductMetadata> aiProducts = new ArrayList<>();

        if (req.getItems() != null) {
            int rowIdx = 0;
            for (ExportDetailRequest item : req.getItems()) {
                rowIdx++;
                if (item.getProductId() == null || item.getProductId() <= 0) {
                    warnings.add("Dòng " + rowIdx + ": Bỏ qua vì productId không hợp lệ");
                    continue;
                }
                if (item.getQuantity() == null || item.getQuantity() <= 0) {
                    continue;
                }
                if (item.getUnitPrice() == null) {
                    continue;
                }

                ShopExportDetail d = new ShopExportDetail();
                d.setExportId(export.getId());
                d.setProductId(item.getProductId());
                // Nếu item có storeId thì dùng, không thì dùng storeId từ header
                d.setStoreId(item.getStoreId() != null ? item.getStoreId() : export.getStoreId());
                d.setImportDetailsId(item.getImportDetailsId());
                d.setQuantity(item.getQuantity());
                d.setUnitPrice(item.getUnitPrice());
                d.setDiscountPercent(item.getDiscountPercent());

                BigDecimal line = item.getUnitPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity()));

                // Áp dụng chiết khấu nếu có
                if (item.getDiscountPercent() != null && item.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal discountMultiplier = BigDecimal.ONE
                            .subtract(item.getDiscountPercent().divide(BigDecimal.valueOf(100), 4,
                                    java.math.RoundingMode.HALF_UP));
                    line = line.multiply(discountMultiplier);
                }

                total = total.add(line);

                details.add(d);

                com.example.inventory_service.dto.UpdateReceiptMetadataRequest.ProductMetadata aiMeta =
                        new com.example.inventory_service.dto.UpdateReceiptMetadataRequest.ProductMetadata();
                aiMeta.setProductId(item.getProductId());
                aiMeta.setQuantity(item.getQuantity());
                aiMeta.setUnitPrice(item.getUnitPrice().doubleValue());
                aiMeta.setTotalPrice(line.doubleValue());
                aiProducts.add(aiMeta);
            }
        }

        if (!details.isEmpty()) {
            detailRepo.saveAll(details);
        }

        // Gửi metadata đã được map sang ai-service (best-effort, không chặn luồng)
        if (!aiProducts.isEmpty()) {
            try {
                com.example.inventory_service.dto.UpdateReceiptMetadataRequest aiReq =
                        new com.example.inventory_service.dto.UpdateReceiptMetadataRequest();
                aiReq.setReceiptType("EXPORT");
                aiReq.setReceiptCode(export.getCode());
                aiReq.setTotalAmount(total.doubleValue());
                aiReq.setProducts(aiProducts);
                aiServiceClient.updateReceiptMetadata(aiReq);
            } catch (Exception ex) {
                logger.warn("Failed to send metadata to ai-service: {}", ex.getMessage());
            }
        }

        if (!warnings.isEmpty()) {
            logger.warn("Export created with warnings: {}", String.join("; ", warnings));
        }

        SupplierExportDto dto = toDto(export, total);
        if (!warnings.isEmpty()) {
            dto.setWarnings(warnings);
        }
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierExportDto> search(ExportStatus status, String code, LocalDate from, LocalDate to, Pageable pageable) {
        long startTime = System.currentTimeMillis();
        LocalDateTime fromDate = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDate = to != null ? to.plusDays(1).atStartOfDay() : null;

        Page<ShopExport> exportPage = exportRepo.searchAllExportsPaged(
                status,
                code,
                fromDate,
                toDate,
                pageable
        );

        logger.debug("Search exports query took {}ms, found {} records", 
                System.currentTimeMillis() - startTime, exportPage.getTotalElements());

        List<Long> exportIds = exportPage.getContent().stream()
                .map(ShopExport::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, BigDecimal> totalsMap = new HashMap<>();
        Map<Long, List<ShopExportDetail>> detailsMap = new HashMap<>();
        Map<Long, com.example.inventory_service.entity.ShopStore> storeMap = new HashMap<>();

        if (!exportIds.isEmpty()) {
            detailRepo.sumTotalsByExportIds(exportIds).forEach(row -> {
                Long id = (Long) row[0];
                BigDecimal total = (BigDecimal) row[1];
                totalsMap.put(id, total);
            });

            List<ShopExportDetail> details = detailRepo.findByExportIdIn(exportIds);
            detailsMap = details.stream().collect(Collectors.groupingBy(ShopExportDetail::getExportId));

            List<Long> storeIds = details.stream()
                    .map(ShopExportDetail::getStoreId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!storeIds.isEmpty()) {
                storeMap.putAll(storeRepo.findAllById(storeIds).stream()
                        .collect(Collectors.toMap(com.example.inventory_service.entity.ShopStore::getId, Function.identity())));
            }
        }

        final Map<Long, List<ShopExportDetail>> detailsMapFinal = detailsMap;
        final Map<Long, com.example.inventory_service.entity.ShopStore> storeMapFinal = storeMap;
        List<SupplierExportDto> dtoPage = exportPage.getContent().stream()
                .map(e -> toDtoWithCalcTotal(
                        e,
                        detailsMapFinal.getOrDefault(e.getId(), List.of()),
                        totalsMap.get(e.getId()),
                        storeMapFinal))
                .toList();

        logger.debug("Total processing time: {}ms", System.currentTimeMillis() - startTime);
        return new PageImpl<>(dtoPage, pageable, exportPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierExportDto> searchPaged(ExportStatus status,
                                               String code,
                                               LocalDate from,
                                               LocalDate to,
                                               String sortField,
                                               String sortDir,
                                               Pageable pageable) {
        long startTime = System.currentTimeMillis();
        LocalDateTime fromDate = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDate = to != null ? to.plusDays(1).atStartOfDay() : null;

        Page<ShopExport> exportPage = exportRepo.searchAllExportsPaged(
                status,
                code,
                fromDate,
                toDate,
                pageable
        );

        List<Long> exportIds = exportPage.getContent().stream()
                .map(ShopExport::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, BigDecimal> totalsMap = new HashMap<>();
        Map<Long, List<ShopExportDetail>> detailsMap = new HashMap<>();
        Map<Long, com.example.inventory_service.entity.ShopStore> storeMap = new HashMap<>();

        if (!exportIds.isEmpty()) {
            detailRepo.sumTotalsByExportIds(exportIds).forEach(row -> {
                Long id = (Long) row[0];
                BigDecimal total = (BigDecimal) row[1];
                totalsMap.put(id, total);
            });

            List<ShopExportDetail> details = detailRepo.findByExportIdIn(exportIds);
            detailsMap = details.stream().collect(Collectors.groupingBy(ShopExportDetail::getExportId));

            List<Long> storeIds = details.stream()
                    .map(ShopExportDetail::getStoreId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!storeIds.isEmpty()) {
                storeMap.putAll(storeRepo.findAllById(storeIds).stream()
                        .collect(Collectors.toMap(com.example.inventory_service.entity.ShopStore::getId, Function.identity())));
            }
        }

        final Map<Long, List<ShopExportDetail>> detailsMapFinal = detailsMap;
        final Map<Long, com.example.inventory_service.entity.ShopStore> storeMapFinal = storeMap;
        List<SupplierExportDto> dtoPage = exportPage.getContent().stream()
                .map(e -> toDtoWithCalcTotal(
                        e,
                        detailsMapFinal.getOrDefault(e.getId(), List.of()),
                        totalsMap.get(e.getId()),
                        storeMapFinal))
                .toList();

        logger.debug("Search paged query took {}ms, processed {} records", 
                System.currentTimeMillis() - startTime, exportPage.getTotalElements());
        return new PageImpl<>(dtoPage, pageable, exportPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierExportDto> searchKeyset(ExportStatus status,
                                                String code,
                                                LocalDate from,
                                                LocalDate to,
                                                LocalDateTime lastDate,
                                                Long lastId,
                                                Pageable pageable) {
        long startTime = System.currentTimeMillis();
        LocalDateTime fromDate = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDate = to != null ? to.plusDays(1).atStartOfDay() : null;

        Page<ShopExport> exportPage = exportRepo.searchAllExportsKeyset(
                status,
                code,
                fromDate,
                toDate,
                lastDate,
                lastId,
                pageable
        );

        List<Long> exportIds = exportPage.getContent().stream()
                .map(ShopExport::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, BigDecimal> totalsMap = new HashMap<>();
        Map<Long, List<ShopExportDetail>> detailsMap = new HashMap<>();
        Map<Long, com.example.inventory_service.entity.ShopStore> storeMap = new HashMap<>();

        if (!exportIds.isEmpty()) {
            detailRepo.sumTotalsByExportIds(exportIds).forEach(row -> {
                Long id = (Long) row[0];
                BigDecimal total = (BigDecimal) row[1];
                totalsMap.put(id, total);
            });

            List<ShopExportDetail> details = detailRepo.findByExportIdIn(exportIds);
            detailsMap = details.stream().collect(Collectors.groupingBy(ShopExportDetail::getExportId));

            List<Long> storeIds = details.stream()
                    .map(ShopExportDetail::getStoreId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!storeIds.isEmpty()) {
                storeMap.putAll(storeRepo.findAllById(storeIds).stream()
                        .collect(Collectors.toMap(com.example.inventory_service.entity.ShopStore::getId, Function.identity())));
            }
        }

        final Map<Long, List<ShopExportDetail>> detailsMapFinal = detailsMap;
        final Map<Long, com.example.inventory_service.entity.ShopStore> storeMapFinal = storeMap;
        List<SupplierExportDto> dtoPage = exportPage.getContent().stream()
                .map(e -> toDtoWithCalcTotal(
                        e,
                        detailsMapFinal.getOrDefault(e.getId(), List.of()),
                        totalsMap.get(e.getId()),
                        storeMapFinal))
                .toList();

        logger.debug("Keyset pagination query took {}ms, processed {} records", 
                System.currentTimeMillis() - startTime, exportPage.getTotalElements());
        return new PageImpl<>(dtoPage, pageable, exportPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierExportDto getById(Long id) {
        ShopExport e = exportRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Export not found: " + id));
        return toDtoWithCalcTotal(e);
    }

    @Override
    @Transactional
    public SupplierExportDto update(Long id, SupplierExportRequest req) {
        // Validation: Phiếu xuất bắt buộc phải có kho và khách hàng
        if (req.getStoreId() == null) {
            throw new IllegalArgumentException("Phiếu xuất kho bắt buộc phải có kho xuất");
        }
        if (req.getCustomerId() == null &&
                (req.getCustomerName() == null || req.getCustomerName().isBlank())) {
            throw new IllegalArgumentException("Phiếu xuất kho bắt buộc phải có thông tin khách hàng");
        }

        ShopExport export = exportRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Export not found: " + id));

        export.setExportType(ExportType.ORDER); // Cố định = ORDER

        if (req.getCode() != null && !req.getCode().isBlank()) {
            export.setCode(req.getCode());
        }
        export.setStoreId(req.getStoreId());
        export.setCustomerId(req.getCustomerId());
        // Lưu thông tin khách hàng nếu có (nhập trực tiếp)
        export.setCustomerName(req.getCustomerName());
        export.setCustomerPhone(req.getCustomerPhone());
        export.setCustomerAddress(req.getCustomerAddress());
        export.setOrderId(req.getOrderId());
        export.setNote(req.getNote());
        export.setDescription(req.getDescription());
        export.setUpdatedAt(LocalDateTime.now());

        // Cập nhật ảnh
        if (req.getAttachmentImages() != null && !req.getAttachmentImages().isEmpty()) {
            String joined = req.getAttachmentImages().stream()
                    .map(this::normalizeImagePath)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(";"));
            export.setAttachmentImage(joined);
        } else {
            export.setAttachmentImage(null);
        }

        export = exportRepo.save(export);

        // Xóa chi tiết cũ
        detailRepo.deleteByExportId(id);

        // Tạo chi tiết mới
        BigDecimal total = BigDecimal.ZERO;
        List<ShopExportDetail> details = new ArrayList<>();

        if (req.getItems() != null) {
            for (ExportDetailRequest item : req.getItems()) {
                if (item.getQuantity() == null || item.getQuantity() <= 0) {
                    continue;
                }
                if (item.getUnitPrice() == null) {
                    continue;
                }

                ShopExportDetail d = new ShopExportDetail();
                d.setExportId(export.getId());
                d.setProductId(item.getProductId());
                // Nếu item có storeId thì dùng, không thì dùng storeId từ header
                d.setStoreId(item.getStoreId() != null ? item.getStoreId() : export.getStoreId());
                d.setImportDetailsId(item.getImportDetailsId());
                d.setQuantity(item.getQuantity());
                d.setUnitPrice(item.getUnitPrice());
                d.setDiscountPercent(item.getDiscountPercent());

                BigDecimal line = item.getUnitPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity()));

                // Áp dụng chiết khấu nếu có
                if (item.getDiscountPercent() != null && item.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal discountMultiplier = BigDecimal.ONE
                            .subtract(item.getDiscountPercent().divide(BigDecimal.valueOf(100), 4,
                                    java.math.RoundingMode.HALF_UP));
                    line = line.multiply(discountMultiplier);
                }

                total = total.add(line);

                details.add(d);
            }
        }

        if (!details.isEmpty()) {
            detailRepo.saveAll(details);
        }

        return toDto(export, total);
    }

    @Override
    @Transactional
    public SupplierExportDto approve(Long id) {
        ShopExport export = exportRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Export not found: " + id));

        if (export.getStatus() != ExportStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể duyệt phiếu đang ở trạng thái PENDING");
        }

        export.setStatus(ExportStatus.APPROVED);
        Long currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            export.setApprovedBy(currentUserId);
            export.setApprovedAt(LocalDateTime.now());
        }
        export.setUpdatedAt(LocalDateTime.now());
        export = exportRepo.save(export);
        // Ghi nhật ký: duyệt phiếu xuất
        try {
            Long uid = getCurrentUserId();
            String uname = getCurrentUsername();
            if (uname == null) uname = "system";
            System.out.println("Preparing to send activity log: action=APPROVE_DELIVERY, resourceType=EXPORT, resourceId=" + export.getId() + ", userId=" + uid + ", username=" + uname + ", code=" + export.getCode());
            sendActivityLog(uid, uname, "APPROVE_DELIVERY", "EXPORT", export.getId(), export.getCode(), "Approved export: " + export.getCode());
        } catch (Exception e) {
            logger.warn("Failed to send activity log for export approve: {}", e.getMessage());
        }

        return toDtoWithCalcTotal(export);
    }

    @Override
    @Transactional
    public SupplierExportDto confirm(Long id) {
        ShopExport export = exportRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Export not found: " + id));

        if (export.getStatus() != ExportStatus.APPROVED) {
            throw new IllegalStateException("Chỉ có thể xuất kho khi phiếu đã được duyệt (APPROVED)");
        }

        // Kiểm tra và trừ tồn kho từ shop_stocks
        List<ShopExportDetail> details = detailRepo.findByExportId(id);

        // Kiểm tra tồn kho trước khi xuất (mỗi dòng có thể khác kho)
        for (ShopExportDetail d : details) {
            Integer quantity = d.getQuantity();
            if (quantity == null || quantity <= 0) {
                continue;
            }

            Long productId = d.getProductId();
            Long storeId = d.getStoreId(); // Lấy từ detail (mỗi dòng có thể khác kho)

            if (storeId == null) {
                throw new IllegalStateException(
                        String.format("Dòng sản phẩm ID %d không có kho xuất", productId));
            }

            // Kiểm tra tồn kho từ shop_stocks
            ShopStock stock = stockRepo.findByProductIdAndStoreId(productId, storeId)
                    .orElseThrow(() -> new IllegalStateException(
                            String.format("Không tìm thấy tồn kho cho sản phẩm ID %d tại kho ID %d",
                                    productId, storeId)));

            if (stock.getQuantity() < quantity) {
                throw new IllegalStateException(
                        String.format("Sản phẩm ID %d không đủ số lượng trong kho ID %d. Tồn: %d, Cần: %d",
                                productId, storeId, stock.getQuantity(), quantity));
            }
        }

        // Cập nhật trạng thái
        export.setStatus(ExportStatus.EXPORTED);
        Long currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            export.setExportedBy(currentUserId);
            export.setExportedAt(LocalDateTime.now());
        }
        export.setUpdatedAt(LocalDateTime.now());
        export = exportRepo.save(export);

        // Ghi nhật ký: xác nhận xuất kho (đã xuất)
        try {
            Long uid = getCurrentUserId();
            String uname = getCurrentUsername();
            if (uname == null) uname = "system";
            System.out.println("Preparing to send activity log: action=APPROVE_DELIVERY, resourceType=EXPORT, resourceId=" + export.getId() + ", userId=" + uid + ", username=" + uname + ", code=" + export.getCode());
            sendActivityLog(uid, uname, "APPROVE_DELIVERY", "EXPORT", export.getId(), export.getCode(), "Confirmed export (exported): " + export.getCode());
        } catch (Exception e) {
            logger.warn("Failed to send activity log for export confirm: {}", e.getMessage());
        }

        // Trừ tồn kho từ shop_stocks (mỗi dòng trừ tại kho riêng)
        for (ShopExportDetail d : details) {
            if (d.getQuantity() != null && d.getQuantity() > 0 && d.getStoreId() != null) {
                Long storeId = d.getStoreId(); // Lấy từ detail
                ShopStock stock = stockRepo.findByProductIdAndStoreId(d.getProductId(), storeId)
                        .orElseThrow(() -> new IllegalStateException(
                                String.format("Không tìm thấy tồn kho cho sản phẩm ID %d tại kho ID %d",
                                        d.getProductId(), storeId)));

                stock.setQuantity(stock.getQuantity() - d.getQuantity());
                stockRepo.save(stock);
            }
        }

        return toDtoWithCalcTotal(export);
    }

    @Override
    @Transactional
    public SupplierExportDto cancel(Long id) {
        ShopExport export = exportRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Export not found: " + id));

        if (export.getStatus() != ExportStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể hủy phiếu đang ở trạng thái PENDING");
        }

        export.setStatus(ExportStatus.CANCELLED);
        export.setUpdatedAt(LocalDateTime.now());
        export = exportRepo.save(export);

        try {
            Long uid = getCurrentUserId();
            String uname = getCurrentUsername();
            if (uname == null) uname = "system";
            System.out.println("Preparing to send activity log: action=CANCEL_ORDER, resourceType=EXPORT, resourceId=" + export.getId() + ", userId=" + uid + ", username=" + uname + ", code=" + export.getCode());
            sendActivityLog(uid, uname, "CANCEL_ORDER", "EXPORT", export.getId(), export.getCode(), "Cancelled export: " + export.getCode());
        } catch (Exception e) {
            logger.warn("Failed to send activity log for export cancel: {}", e.getMessage());
        }

        return toDtoWithCalcTotal(export);
    }

    @Override
    @Transactional
    public SupplierExportDto reject(Long id) {
        ShopExport export = exportRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Export not found: " + id));

        if (export.getStatus() != ExportStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể từ chối phiếu đang ở trạng thái PENDING");
        }

        export.setStatus(ExportStatus.REJECTED);
        Long currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            export.setRejectedBy(currentUserId);
            export.setRejectedAt(LocalDateTime.now());
        }
        export.setUpdatedAt(LocalDateTime.now());
        export = exportRepo.save(export);

        try {
            Long uid = getCurrentUserId();
            String uname = getCurrentUsername();
            if (uname == null) uname = "system";
            System.out.println("Preparing to send activity log: action=CANCEL_ORDER, resourceType=EXPORT, resourceId=" + export.getId() + ", userId=" + uid + ", username=" + uname + ", code=" + export.getCode());
            sendActivityLog(uid, uname, "CANCEL_ORDER", "EXPORT", export.getId(), export.getCode(), "Rejected export: " + export.getCode());
        } catch (Exception e) {
            logger.warn("Failed to send activity log for export reject: {}", e.getMessage());
        }

        return toDtoWithCalcTotal(export);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierExportDto> getAll(Pageable pageable) {
        Page<ShopExport> exportPage = exportRepo.findAll(pageable);
        List<Long> exportIds = exportPage.getContent().stream()
                .map(ShopExport::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, BigDecimal> totalsMap = new HashMap<>();
        Map<Long, List<ShopExportDetail>> detailsMap = new HashMap<>();
        Map<Long, com.example.inventory_service.entity.ShopStore> storeMap = new HashMap<>();

        if (!exportIds.isEmpty()) {
            detailRepo.sumTotalsByExportIds(exportIds).forEach(row -> {
                Long id = (Long) row[0];
                BigDecimal total = (BigDecimal) row[1];
                totalsMap.put(id, total);
            });

            List<ShopExportDetail> details = detailRepo.findByExportIdIn(exportIds);
            detailsMap = details.stream().collect(Collectors.groupingBy(ShopExportDetail::getExportId));

            List<Long> storeIds = details.stream()
                    .map(ShopExportDetail::getStoreId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!storeIds.isEmpty()) {
                storeMap.putAll(storeRepo.findAllById(storeIds).stream()
                        .collect(Collectors.toMap(com.example.inventory_service.entity.ShopStore::getId, Function.identity())));
            }
        }

        final Map<Long, List<ShopExportDetail>> detailsMapFinal = detailsMap;
        final Map<Long, com.example.inventory_service.entity.ShopStore> storeMapFinal = storeMap;
        List<SupplierExportDto> dtoPage = exportPage.getContent().stream()
                .map(e -> toDtoWithCalcTotal(
                        e,
                        detailsMapFinal.getOrDefault(e.getId(), List.of()),
                        totalsMap.get(e.getId()),
                        storeMapFinal))
                .toList();

        return new PageImpl<>(dtoPage, pageable, exportPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierExportDto> getByStore(Long storeId, Pageable pageable) {
        Page<ShopExport> exportPage = exportRepo.findByStoreId(storeId, pageable);
        List<Long> exportIds = exportPage.getContent().stream()
                .map(ShopExport::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, BigDecimal> totalsMap = new HashMap<>();
        Map<Long, List<ShopExportDetail>> detailsMap = new HashMap<>();
        Map<Long, com.example.inventory_service.entity.ShopStore> storeMap = new HashMap<>();

        if (!exportIds.isEmpty()) {
            detailRepo.sumTotalsByExportIds(exportIds).forEach(row -> {
                Long id = (Long) row[0];
                BigDecimal total = (BigDecimal) row[1];
                totalsMap.put(id, total);
            });

            List<ShopExportDetail> details = detailRepo.findByExportIdIn(exportIds);
            detailsMap = details.stream().collect(Collectors.groupingBy(ShopExportDetail::getExportId));

            List<Long> storeIds = details.stream()
                    .map(ShopExportDetail::getStoreId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!storeIds.isEmpty()) {
                storeMap.putAll(storeRepo.findAllById(storeIds).stream()
                        .collect(Collectors.toMap(com.example.inventory_service.entity.ShopStore::getId, Function.identity())));
            }
        }

        final Map<Long, List<ShopExportDetail>> detailsMapFinal = detailsMap;
        final Map<Long, com.example.inventory_service.entity.ShopStore> storeMapFinal = storeMap;
        List<SupplierExportDto> dtoPage = exportPage.getContent().stream()
                .map(e -> toDtoWithCalcTotal(
                        e,
                        detailsMapFinal.getOrDefault(e.getId(), List.of()),
                        totalsMap.get(e.getId()),
                        storeMapFinal))
                .toList();

        return new PageImpl<>(dtoPage, pageable, exportPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierExportDto> getByOrder(Long orderId, Pageable pageable) {
        Page<ShopExport> exportPage = exportRepo.findByOrderId(orderId, pageable);
        List<Long> exportIds = exportPage.getContent().stream()
                .map(ShopExport::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, BigDecimal> totalsMap = new HashMap<>();
        Map<Long, List<ShopExportDetail>> detailsMap = new HashMap<>();
        Map<Long, com.example.inventory_service.entity.ShopStore> storeMap = new HashMap<>();

        if (!exportIds.isEmpty()) {
            detailRepo.sumTotalsByExportIds(exportIds).forEach(row -> {
                Long id = (Long) row[0];
                BigDecimal total = (BigDecimal) row[1];
                totalsMap.put(id, total);
            });

            List<ShopExportDetail> details = detailRepo.findByExportIdIn(exportIds);
            detailsMap = details.stream().collect(Collectors.groupingBy(ShopExportDetail::getExportId));

            List<Long> storeIds = details.stream()
                    .map(ShopExportDetail::getStoreId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!storeIds.isEmpty()) {
                storeMap.putAll(storeRepo.findAllById(storeIds).stream()
                        .collect(Collectors.toMap(com.example.inventory_service.entity.ShopStore::getId, Function.identity())));
            }
        }

        final Map<Long, List<ShopExportDetail>> detailsMapFinal = detailsMap;
        final Map<Long, com.example.inventory_service.entity.ShopStore> storeMapFinal = storeMap;
        List<SupplierExportDto> dtoPage = exportPage.getContent().stream()
                .map(e -> toDtoWithCalcTotal(
                        e,
                        detailsMapFinal.getOrDefault(e.getId(), List.of()),
                        totalsMap.get(e.getId()),
                        storeMapFinal))
                .toList();

        return new PageImpl<>(dtoPage, pageable, exportPage.getTotalElements());
    }

    // ========= HELPER METHODS ========= //
    
    /**
     * Lấy userId hiện tại từ SecurityContext (username) và query từ database
     */
    private Long getCurrentUserId() {
        try {
            if (userRepo == null) {
                System.err.println("⚠️ userRepo is null in getCurrentUserId");
                return null;
            }
            org.springframework.security.core.Authentication auth = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                String username = auth.getName();
                System.out.println("🔍 Getting userId for username: " + username);
                java.util.Optional<Long> userIdOpt = userRepo.findUserIdByUsername(username);
                if (userIdOpt.isPresent()) {
                    System.out.println("✅ Found userId: " + userIdOpt.get() + " for username: " + username);
                    return userIdOpt.get();
                } else {
                    System.out.println("⚠️ No userId found for username: " + username);
                }
            } else {
                System.err.println("⚠️ No authentication found in SecurityContext");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to get current userId: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Lấy fullName từ userId bằng cách query database
     */
    private String getUserFullNameFromId(Long userId) {
        try {
            if (userRepo == null) {
                return null;
            }
            java.util.Optional<String> fullName = userRepo.findFullNameByUserId(userId);
            if (fullName.isPresent() && !fullName.get().trim().isEmpty()) {
                return fullName.get().trim();
            }
            // Nếu không có fullName, lấy username
            java.util.Optional<String> username = userRepo.findUsernameByUserId(userId);
            return username.orElse(null);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to get user full name from userId " + userId + ": " + e.getMessage());
            return null;
        }
    }

    private String normalizeImagePath(String raw) {
        if (raw == null || raw.isBlank())
            return null;

        int idx = raw.indexOf("/uploads/");
        if (idx >= 0) {
            return raw.substring(idx);
        }

        if (!raw.startsWith("/")) {
            return "/" + raw;
        }

        return raw;
    }

    private SupplierExportDto toDtoWithCalcTotal(ShopExport e) {
        List<ShopExportDetail> details = detailRepo.findByExportId(e.getId());

        Map<Long, com.example.inventory_service.entity.ShopStore> storeMap = Collections.emptyMap();
        if (details != null && !details.isEmpty()) {
            List<Long> storeIds = details.stream()
                    .map(ShopExportDetail::getStoreId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!storeIds.isEmpty()) {
                storeMap = storeRepo.findAllById(storeIds).stream()
                        .collect(Collectors.toMap(com.example.inventory_service.entity.ShopStore::getId, Function.identity()));
            }
        }

        return toDtoWithCalcTotal(e, details, null, storeMap);
    }

    private SupplierExportDto toDtoWithCalcTotal(
            ShopExport e,
            List<ShopExportDetail> details,
            BigDecimal precomputedTotal,
            Map<Long, com.example.inventory_service.entity.ShopStore> storeMap) {
        BigDecimal total = precomputedTotal != null ? precomputedTotal : BigDecimal.ZERO;
        List<ExportDetailDto> itemDtos = new ArrayList<>();

        if (details != null) {
            for (ShopExportDetail d : details) {
                if (precomputedTotal == null) {
                    if (d.getUnitPrice() != null && d.getQuantity() != null) {
                BigDecimal line = d.getUnitPrice()
                        .multiply(BigDecimal.valueOf(d.getQuantity()));

                if (d.getDiscountPercent() != null && d.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal discountMultiplier = BigDecimal.ONE
                            .subtract(d.getDiscountPercent().divide(BigDecimal.valueOf(100), 4,
                                    java.math.RoundingMode.HALF_UP));
                    line = line.multiply(discountMultiplier);
                }

                total = total.add(line);
                    }
                }

                ExportDetailDto itemDto = new ExportDetailDto();
                itemDto.setId(d.getId());
                itemDto.setProductId(d.getProductId());
                itemDto.setStoreId(d.getStoreId());
                itemDto.setQuantity(d.getQuantity());
                itemDto.setUnitPrice(d.getUnitPrice());
                itemDto.setDiscountPercent(d.getDiscountPercent());
                itemDto.setImportDetailsId(d.getImportDetailsId());
                itemDto.setProductCode(null);
                itemDto.setProductName(null);
                itemDto.setUnit(null);
                itemDto.setStoreName(null);
                itemDto.setStoreCode(null);

                if (d.getStoreId() != null && storeMap != null && !storeMap.isEmpty()) {
                    com.example.inventory_service.entity.ShopStore store = storeMap.get(d.getStoreId());
                    if (store != null) {
                        itemDto.setStoreName(store.getName());
                        itemDto.setStoreCode(store.getCode());
                    }
                }

                itemDtos.add(itemDto);
            }
        }

        SupplierExportDto dto = toDto(e, total, storeMap);
        dto.setItems(itemDtos);
        return dto;
    }

    private SupplierExportDto toDto(ShopExport e, BigDecimal total) {
        return toDto(e, total, null);
    }

    private SupplierExportDto toDto(ShopExport e, BigDecimal total, Map<Long, com.example.inventory_service.entity.ShopStore> storeMap) {
        SupplierExportDto dto = new SupplierExportDto();
        dto.setId(e.getId());
        dto.setCode(e.getCode());
        dto.setStoreId(e.getStoreId());
        dto.setCustomerId(e.getCustomerId());
        dto.setStatus(e.getStatus() != null ? e.getStatus().name() : null);
        // Convert LocalDateTime to Date for DTO compatibility
        if (e.getExportsDate() != null) {
            dto.setExportsDate(java.sql.Timestamp.valueOf(e.getExportsDate()));
        }
        dto.setNote(e.getNote());
        dto.setTotalValue(total);

        // Lấy thông tin khách hàng từ entity (đã lưu khi tạo/cập nhật)
        dto.setCustomerName(e.getCustomerName());
        dto.setCustomerPhone(e.getCustomerPhone());
        dto.setCustomerAddress(e.getCustomerAddress());
        // TODO: Nếu customerId có và customerName chưa có, có thể fetch từ customer
        // service
        // if (e.getCustomerId() != null && e.getCustomerName() == null) {
        // // Fetch from customer service
        // }

        // Lấy thông tin kho từ map nếu có, hoặc query từ DB
        if (e.getStoreId() != null) {
            if (storeMap != null && !storeMap.isEmpty()) {
                com.example.inventory_service.entity.ShopStore store = storeMap.get(e.getStoreId());
                if (store != null) {
                    dto.setStoreName(store.getName());
                }
            } else {
            storeRepo.findById(e.getStoreId()).ifPresent(store -> {
                dto.setStoreName(store.getName());
            });
            }
        }

        // Map ảnh
        List<String> images = new ArrayList<>();
        String raw = e.getAttachmentImage();
        if (raw != null && !raw.isBlank()) {
            images = Arrays.stream(raw.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        dto.setAttachmentImages(images);

        // Map audit fields với userId và timestamp
        dto.setCreatedBy(e.getCreatedBy());
        if (e.getCreatedAt() != null) {
            dto.setCreatedAt(java.sql.Timestamp.valueOf(e.getCreatedAt()));
        }
        dto.setApprovedBy(e.getApprovedBy());
        if (e.getApprovedAt() != null) {
            dto.setApprovedAt(java.sql.Timestamp.valueOf(e.getApprovedAt()));
        }
        dto.setRejectedBy(e.getRejectedBy());
        if (e.getRejectedAt() != null) {
            dto.setRejectedAt(java.sql.Timestamp.valueOf(e.getRejectedAt()));
        }
        dto.setExportedBy(e.getExportedBy());
        if (e.getExportedAt() != null) {
            dto.setExportedAt(java.sql.Timestamp.valueOf(e.getExportedAt()));
        }
        
        // Lấy tên user và role từ userId
        try {
            if (userRepo == null) {
                System.err.println("⚠️ userRepo is null, cannot fetch user names");
            } else {
                if (e.getCreatedBy() != null) {
                    String createdByUsername = getUserFullNameFromId(e.getCreatedBy());
                    if (createdByUsername != null && !createdByUsername.trim().isEmpty()) {
                        dto.setCreatedByName(createdByUsername);
                        System.out.println("✅ Set createdByName: " + createdByUsername + " for userId: " + e.getCreatedBy());
                    } else {
                        System.out.println("⚠️ Could not get name for createdBy userId: " + e.getCreatedBy());
                    }
                    String createdByRole = getUserRoleFromId(e.getCreatedBy());
                    if (createdByRole != null && !createdByRole.trim().isEmpty()) {
                        dto.setCreatedByRole(createdByRole);
                        System.out.println("✅ Set createdByRole: " + createdByRole + " for userId: " + e.getCreatedBy());
                    } else {
                        System.out.println("⚠️ Could not get role for createdBy userId: " + e.getCreatedBy());
                    }
                }
                if (e.getApprovedBy() != null) {
                    String approvedByUsername = getUserFullNameFromId(e.getApprovedBy());
                    if (approvedByUsername != null && !approvedByUsername.trim().isEmpty()) {
                        dto.setApprovedByName(approvedByUsername);
                        System.out.println("✅ Set approvedByName: " + approvedByUsername + " for userId: " + e.getApprovedBy());
                    } else {
                        System.out.println("⚠️ Could not get name for approvedBy userId: " + e.getApprovedBy());
                    }
                    String approvedByRole = getUserRoleFromId(e.getApprovedBy());
                    if (approvedByRole != null && !approvedByRole.trim().isEmpty()) {
                        dto.setApprovedByRole(approvedByRole);
                        System.out.println("✅ Set approvedByRole: " + approvedByRole + " for userId: " + e.getApprovedBy());
                    } else {
                        System.out.println("⚠️ Could not get role for approvedBy userId: " + e.getApprovedBy());
                    }
                }
                if (e.getRejectedBy() != null) {
                    String rejectedByUsername = getUserFullNameFromId(e.getRejectedBy());
                    if (rejectedByUsername != null && !rejectedByUsername.trim().isEmpty()) {
                        dto.setRejectedByName(rejectedByUsername);
                        System.out.println("✅ Set rejectedByName: " + rejectedByUsername + " for userId: " + e.getRejectedBy());
                    } else {
                        System.out.println("⚠️ Could not get name for rejectedBy userId: " + e.getRejectedBy());
                    }
                    String rejectedByRole = getUserRoleFromId(e.getRejectedBy());
                    if (rejectedByRole != null && !rejectedByRole.trim().isEmpty()) {
                        dto.setRejectedByRole(rejectedByRole);
                        System.out.println("✅ Set rejectedByRole: " + rejectedByRole + " for userId: " + e.getRejectedBy());
                    } else {
                        System.out.println("⚠️ Could not get role for rejectedBy userId: " + e.getRejectedBy());
                    }
                }
                if (e.getExportedBy() != null) {
                    String exportedByUsername = getUserFullNameFromId(e.getExportedBy());
                    if (exportedByUsername != null && !exportedByUsername.trim().isEmpty()) {
                        dto.setExportedByName(exportedByUsername);
                        System.out.println("✅ Set exportedByName: " + exportedByUsername + " for userId: " + e.getExportedBy());
                    } else {
                        System.out.println("⚠️ Could not get name for exportedBy userId: " + e.getExportedBy());
                    }
                    String exportedByRole = getUserRoleFromId(e.getExportedBy());
                    if (exportedByRole != null && !exportedByRole.trim().isEmpty()) {
                        dto.setExportedByRole(exportedByRole);
                        System.out.println("✅ Set exportedByRole: " + exportedByRole + " for userId: " + e.getExportedBy());
                    } else {
                        System.out.println("⚠️ Could not get role for exportedBy userId: " + e.getExportedBy());
                    }
                }
            }
        } catch (Exception ex) {
            // Nếu có lỗi khi lấy user name, bỏ qua và tiếp tục
            System.err.println("⚠️ Failed to get user names: " + ex.getMessage());
            ex.printStackTrace();
        }

        return dto;
    }
    
    /**
     * Gửi activity log tới auth-service bằng Java 11 HttpClient.
     */
    private void sendActivityLog(Long userId, String username, String action, String resourceType, Long resourceId, String resourceName, String details) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("username", username);
            // include displayName if available
            try {
                payload.put("displayName", getUserFullName(username));
            } catch (Exception ignored) {
                payload.put("displayName", username);
            }
            payload.put("action", action);
            payload.put("resourceType", resourceType);
            payload.put("resourceId", resourceId);
            payload.put("resourceName", resourceName);
            payload.put("details", details);

            String json = mapper.writeValueAsString(payload);
            HttpClient client = HttpClient.newHttpClient();
            String authUrl = System.getenv("AUTH_SERVICE_URL");
            if (authUrl == null || authUrl.isBlank()) {
                authUrl = "http://localhost:8080";
            }
            String endpoint = authUrl.endsWith("/") ? authUrl + "api/internal/activity-logs" : authUrl + "/api/internal/activity-logs";
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json");
            String token = System.getenv("ACTIVITY_LOG_SERVICE_TOKEN");
            if (token != null && !token.isBlank()) {
                builder.header("X-Activity-Log-Token", token);
            }
            HttpRequest req = builder.POST(HttpRequest.BodyPublishers.ofString(json)).build();
            System.out.println("Sending activity log to: " + endpoint + " payload: " + json);
            client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> {
                        if (resp.statusCode() >= 400) {
                            logger.warn("Auth-service returned {} when sending activity log: {}", resp.statusCode(), resp.body());
                        } else {
                            System.out.println("Activity log sent, status: " + resp.statusCode());
                        }
                    })
                    .exceptionally(ex -> {
                        logger.warn("Failed to send activity log: {}", ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            logger.warn("sendActivityLog failed: {}", e.getMessage());
        }
    }
    
    /**
     * Lấy role từ userId bằng cách query database
     */
    private String getUserRoleFromId(Long userId) {
        try {
            if (userRepo == null) {
                return null;
            }
            java.util.Optional<String> role = userRepo.findRoleByUserId(userId);
            return role.orElse(null);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to get user role from userId " + userId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Lấy tên đầy đủ của user từ username
     */
    @SuppressWarnings("unused")
    private String getUserFullName(String username) {
        try {
            if (userRepo == null) {
                return username;
            }
            return userRepo.findFullNameByUsername(username)
                    .map(name -> name.trim())
                    .filter(name -> !name.isEmpty())
                    .orElse(username);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to get user full name: " + e.getMessage());
            return username;
        }
    }

    private String getCurrentUsername() {
        try {
            org.springframework.security.core.Authentication auth = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                return auth.getName();
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ShopExport exportEntity = exportRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Phiếu xuất không tồn tại với id: " + id));

        // Lấy role của user hiện tại
        Long currentUserId = getCurrentUserId();
        String userRole = getUserRoleFromId(currentUserId);

        // Kiểm tra quyền: chỉ ADMIN và MANAGER mới được xóa
        if (userRole == null || (!userRole.equals("ADMIN") && !userRole.equals("MANAGER"))) {
            throw new com.example.inventory_service.exception.BadRequestException("Chỉ ADMIN và MANAGER mới có quyền xóa phiếu xuất");
        }

        // Nếu là ADMIN: xóa trực tiếp
        if ("ADMIN".equals(userRole)) {
            // Xóa chi tiết trước
            detailRepo.deleteByExportId(id);
            // Xóa phiếu xuất
            exportRepo.delete(exportEntity);
            logger.info("Admin đã xóa phiếu xuất id: {}", id);
            return;
        }

        // Nếu là MANAGER: đánh dấu status = CANCELLED và lưu note "DELETE_REQUESTED"
        // ADMIN sẽ cần duyệt để xóa thực sự
        if ("MANAGER".equals(userRole)) {
            exportEntity.setStatus(ExportStatus.CANCELLED);
            String currentNote = exportEntity.getNote() != null ? exportEntity.getNote() : "";
            String deleteRequestNote = "[DELETE_REQUESTED_BY_MANAGER] " + getCurrentUsername() + " đã yêu cầu xóa phiếu này. Cần ADMIN duyệt để xóa thực sự.";
            exportEntity.setNote(currentNote.isEmpty() ? deleteRequestNote : currentNote + "\n" + deleteRequestNote);
            exportEntity.setUpdatedAt(LocalDateTime.now());
            exportRepo.save(exportEntity);
            logger.info("Manager đã yêu cầu xóa phiếu xuất id: {}, cần ADMIN duyệt", id);
            throw new com.example.inventory_service.exception.BadRequestException("Đã gửi yêu cầu xóa. ADMIN cần duyệt để xóa thực sự.");
        }
    }
}

