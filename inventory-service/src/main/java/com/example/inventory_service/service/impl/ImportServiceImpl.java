package com.example.inventory_service.service.impl;

import com.example.inventory_service.client.ProductServiceClient;
import com.example.inventory_service.dto.ImportDetailDto;
import com.example.inventory_service.dto.ImportDetailRequest;
import com.example.inventory_service.dto.SupplierImportDto;
import com.example.inventory_service.dto.SupplierImportRequest;
import com.example.inventory_service.entity.ImportStatus;
import com.example.inventory_service.entity.ImportType;
import com.example.inventory_service.entity.ShopImport;
import com.example.inventory_service.entity.ShopImportDetail;
import com.example.inventory_service.exception.NotFoundException;
import com.example.inventory_service.repository.ShopImportDetailRepository;
import com.example.inventory_service.repository.ShopImportRepository;
import com.example.inventory_service.repository.ShopStockRepository;
import com.example.inventory_service.entity.ShopStock;
import com.example.inventory_service.service.ImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ImportServiceImpl implements ImportService {

    private static final Logger logger = LoggerFactory.getLogger(ImportServiceImpl.class);

    private final ShopImportRepository importRepo;
    private final ShopImportDetailRepository detailRepo;
    private final ProductServiceClient productClient;
    private final com.example.inventory_service.client.AiServiceClient aiServiceClient;
    private final com.example.inventory_service.repository.ShopStoreRepository storeRepo;
    private final ShopStockRepository stockRepo;
    private com.example.inventory_service.repository.UserQueryRepository userRepo;

    public ImportServiceImpl(
            ShopImportRepository importRepo,
            ShopImportDetailRepository detailRepo,
            ProductServiceClient productClient,
            com.example.inventory_service.client.AiServiceClient aiServiceClient,
            com.example.inventory_service.repository.ShopStoreRepository storeRepo,
            ShopStockRepository stockRepo,
            com.example.inventory_service.repository.UserQueryRepository userRepo) {
        this.importRepo = importRepo;
        this.detailRepo = detailRepo;
        this.productClient = productClient;
        this.aiServiceClient = aiServiceClient;
        this.storeRepo = storeRepo;
        this.stockRepo = stockRepo;
        this.userRepo = userRepo;
    }

    @Override
    @Transactional
    public SupplierImportDto create(SupplierImportRequest request) {
        // Validation: Phiếu nhập bắt buộc phải có kho và nhà cung cấp
        if (request.getStoreId() == null) {
            throw new IllegalArgumentException("Phiếu nhập kho bắt buộc phải có kho nhập");
        }
        if (request.getSupplierId() == null) {
            throw new IllegalArgumentException("Phiếu nhập kho bắt buộc phải có nhà cung cấp");
        }

        ShopImport im = new ShopImport();

        if (request.getCode() != null && !request.getCode().isBlank()) {
            im.setCode(request.getCode());
        } else {
            im.setCode("PNNCC" + System.currentTimeMillis());
        }

        // Lấy supplier type để set vào importType
        ImportType importType = ImportType.SUPPLIER; // Default
        try {
            var supplierInfo = productClient.getSupplier(request.getSupplierId());
            if (supplierInfo != null && supplierInfo.getType() != null) {
                // Convert String to ImportType enum
                String typeStr = supplierInfo.getType();
                try {
                    importType = ImportType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    logger.warn("Unknown supplier type: {}, using default SUPPLIER", typeStr);
                }
            }
        } catch (Exception ex) {
            logger.warn("Failed to get supplier type, using default SUPPLIER: {}", ex.getMessage());
        }
        im.setImportType(importType);
        im.setStoreId(request.getStoreId());
        im.setSupplierId(request.getSupplierId());
        im.setOrderId(request.getOrderId());

        im.setNote(limitNote(request.getNote()));
        im.setDescription(request.getDescription());
        im.setStatus(ImportStatus.PENDING);
        LocalDateTime now = LocalDateTime.now();
        im.setImportsDate(now);
        im.setCreatedAt(now);
        im.setUpdatedAt(now);
        
        // Set createdBy từ userId nếu có
        Long currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            im.setCreatedBy(currentUserId);
        } else if (im.getUserId() != null) {
            im.setCreatedBy(im.getUserId());
        }

        // Lưu ảnh
        if (request.getAttachmentImages() != null && !request.getAttachmentImages().isEmpty()) {
            String joined = request.getAttachmentImages().stream()
                    .map(this::normalizeImagePath)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(";"));
            im.setAttachmentImage(joined);
        } else {
            im.setAttachmentImage(null);
        }

        im = importRepo.save(im);

        // Ghi nhật ký hoạt động: tạo phiếu nhập (receipt)
        try {
            Long uid = currentUserId;
            String uname = getCurrentUsername();
            if (uname == null) uname = "system";
            System.out.println("Preparing to send activity log: action=CREATE_RECEIPT, resourceType=IMPORT, resourceId=" + im.getId() + ", userId=" + uid + ", username=" + uname + ", code=" + im.getCode());
            sendActivityLog(uid, uname, "CREATE_RECEIPT", "IMPORT", im.getId(), im.getCode(), "Created import: " + im.getCode());
        } catch (Exception e) {
            logger.warn("Failed to send activity log for import create: {}", e.getMessage());
        }

        // Lưu chi tiết
        BigDecimal total = BigDecimal.ZERO;
        List<ShopImportDetail> details = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<com.example.inventory_service.dto.UpdateReceiptMetadataRequest.ProductMetadata> aiProducts = new ArrayList<>();

        if (request.getItems() != null) {
            int rowIdx = 0;
            for (ImportDetailRequest item : request.getItems()) {
                rowIdx++;
                if (item.getProductId() == null || item.getProductId() <= 0) {
                    warnings.add("Dòng " + rowIdx + ": Bỏ qua vì productId không hợp lệ");
                    continue;
                }
                if (item.getQuantity() == null || item.getQuantity() <= 0)
                    continue;
                if (item.getUnitPrice() == null)
                    continue;

                ShopImportDetail d = new ShopImportDetail();
                d.setImportId(im.getId());
                d.setProductId(item.getProductId());
                // Nếu item có storeId thì dùng, không thì dùng storeId từ header
                d.setStoreId(item.getStoreId() != null ? item.getStoreId() : im.getStoreId());
                d.setQuantity(item.getQuantity());
                d.setUnitPrice(item.getUnitPrice());
                d.setDiscountPercent(item.getDiscountPercent() != null ? item.getDiscountPercent() : BigDecimal.ZERO);

                details.add(d);

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
                aiReq.setReceiptType("IMPORT");
                aiReq.setReceiptCode(im.getCode());
                aiReq.setTotalAmount(total.doubleValue());
                aiReq.setProducts(aiProducts);
                aiServiceClient.updateReceiptMetadata(aiReq);
            } catch (Exception ex) {
                logger.warn("Failed to send metadata to ai-service: {}", ex.getMessage());
            }
        }

        if (!warnings.isEmpty()) {
            logger.warn("Import created with warnings: {}", String.join("; ", warnings));
        }

        SupplierImportDto dto = toDto(im, total);
        if (!warnings.isEmpty()) {
            dto.setWarnings(warnings);
        }
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierImportDto> search(String status, String code, LocalDate from, LocalDate to) {
        ImportStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = ImportStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid import status: {}", status);
            }
        }
        LocalDateTime fromDate = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDate = to != null ? to.plusDays(1).atStartOfDay() : null;

        // Use paginated search with large page size
        Page<ShopImport> page = importRepo.searchAllImportsPaged(
                statusEnum,
                code,
                fromDate,
                toDate,
                org.springframework.data.domain.PageRequest.of(0, 1000)); // Limit to 1000 records

        List<SupplierImportDto> result = new ArrayList<>();
        for (ShopImport im : page.getContent()) {
            result.add(toDtoWithCalcTotal(im));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierImportDto> searchPaged(String status,
                                               String code,
                                               LocalDate from,
                                               LocalDate to,
                                               String sortField,
                                               String sortDir,
                                               Pageable pageable) {
        long startTime = System.currentTimeMillis();
        ImportStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = ImportStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid import status: {}", status);
            }
        }
        LocalDateTime fromDate = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDate = to != null ? to.plusDays(1).atStartOfDay() : null;

        Page<ShopImport> importPage = importRepo.searchAllImportsPaged(
                statusEnum,
                code,
                fromDate,
                toDate,
                pageable
        );

        List<Long> importIds = importPage.getContent().stream()
                .map(ShopImport::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, BigDecimal> totalsMap = new HashMap<>();
        Map<Long, List<ShopImportDetail>> detailsMap = new HashMap<>();
        Map<Long, com.example.inventory_service.entity.ShopStore> storeMap = new HashMap<>();

        if (!importIds.isEmpty()) {
            detailRepo.sumTotalsByImportIds(importIds).forEach(row -> {
                Long id = (Long) row[0];
                BigDecimal total = (BigDecimal) row[1];
                totalsMap.put(id, total);
            });

            List<ShopImportDetail> details = detailRepo.findByImportIdIn(importIds);
            detailsMap = details.stream().collect(Collectors.groupingBy(ShopImportDetail::getImportId));

            List<Long> storeIds = details.stream()
                    .map(ShopImportDetail::getStoreId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!storeIds.isEmpty()) {
                storeMap.putAll(storeRepo.findAllById(storeIds).stream()
                        .collect(Collectors.toMap(com.example.inventory_service.entity.ShopStore::getId, Function.identity())));
            }
        }

        final Map<Long, List<ShopImportDetail>> detailsMapFinal = detailsMap;
        final Map<Long, com.example.inventory_service.entity.ShopStore> storeMapFinal = storeMap;
        List<SupplierImportDto> dtoPage = importPage.getContent().stream()
                .map(im -> toDtoWithCalcTotal(
                        im,
                        detailsMapFinal.getOrDefault(im.getId(), List.of()),
                        totalsMap.get(im.getId()),
                        storeMapFinal))
                .toList();

        logger.debug("Search paged query took {}ms, processed {} records",
                System.currentTimeMillis() - startTime, importPage.getTotalElements());
        return new PageImpl<>(dtoPage, pageable, importPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierImportDto getById(Long id) {
        ShopImport im = importRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Import not found: " + id));
        return toDtoWithCalcTotal(im);
    }

    @Override
    @Transactional
    public SupplierImportDto update(Long id, SupplierImportRequest request) {
        // Validation: Phiếu nhập bắt buộc phải có kho và nhà cung cấp
        if (request.getStoreId() == null) {
            throw new IllegalArgumentException("Phiếu nhập kho bắt buộc phải có kho nhập");
        }
        if (request.getSupplierId() == null) {
            throw new IllegalArgumentException("Phiếu nhập kho bắt buộc phải có nhà cung cấp");
        }

        ShopImport im = importRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Import not found: " + id));

        // Lấy supplier type để set vào importType
        ImportType importType = im.getImportType(); // Giữ nguyên nếu đã có
        if (request.getSupplierId() != null) {
            try {
                var supplierInfo = productClient.getSupplier(request.getSupplierId());
                if (supplierInfo != null && supplierInfo.getType() != null) {
                    String typeStr = supplierInfo.getType();
                    try {
                        importType = ImportType.valueOf(typeStr);
                    } catch (IllegalArgumentException e) {
                        logger.warn("Unknown supplier type: {}, keeping existing type", typeStr);
                    }
                }
            } catch (Exception ex) {
                logger.warn("Failed to get supplier type: {}", ex.getMessage());
            }
        }
        im.setImportType(importType);

        if (request.getCode() != null && !request.getCode().isBlank()) {
            im.setCode(request.getCode());
        }
        im.setStoreId(request.getStoreId());
        im.setSupplierId(request.getSupplierId());
        im.setOrderId(request.getOrderId());
        im.setOrderId(request.getOrderId());

        im.setNote(limitNote(request.getNote()));
        im.setDescription(request.getDescription());
        im.setUpdatedAt(LocalDateTime.now());

        // Cập nhật ảnh
        if (request.getAttachmentImages() != null && !request.getAttachmentImages().isEmpty()) {
            String joined = request.getAttachmentImages().stream()
                    .map(this::normalizeImagePath)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(";"));
            im.setAttachmentImage(joined);
        } else {
            im.setAttachmentImage(null);
        }

        im = importRepo.save(im);

        // Xóa chi tiết cũ và tạo mới
        detailRepo.deleteByImportId(id);

        BigDecimal total = BigDecimal.ZERO;
        List<ShopImportDetail> details = new ArrayList<>();

        if (request.getItems() != null) {
            for (ImportDetailRequest item : request.getItems()) {
                if (item.getQuantity() == null || item.getQuantity() <= 0)
                    continue;
                if (item.getUnitPrice() == null)
                    continue;

                ShopImportDetail d = new ShopImportDetail();
                d.setImportId(im.getId());
                d.setProductId(item.getProductId());
                // Nếu item có storeId thì dùng, không thì dùng storeId từ header
                d.setStoreId(item.getStoreId() != null ? item.getStoreId() : im.getStoreId());
                d.setQuantity(item.getQuantity());
                d.setUnitPrice(item.getUnitPrice());
                d.setDiscountPercent(item.getDiscountPercent() != null ? item.getDiscountPercent() : BigDecimal.ZERO);

                details.add(d);

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
            }
        }

        if (!details.isEmpty()) {
            detailRepo.saveAll(details);
        }

        return toDto(im, total);
    }

    @Override
    @Transactional
    public SupplierImportDto approve(Long id) {
        ShopImport im = importRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Import not found: " + id));

        if (im.getStatus() != ImportStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể duyệt phiếu đang ở trạng thái PENDING");
        }

        im.setStatus(ImportStatus.APPROVED);
        Long currentUserId = getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        if (currentUserId != null) {
            im.setApprovedBy(currentUserId);
            im.setApprovedAt(now);
        }
        im.setUpdatedAt(now);
        im = importRepo.save(im);
        try {
            Long uid = getCurrentUserId();
            String uname = getCurrentUsername();
            if (uname == null) uname = "system";
            System.out.println("Preparing to send activity log: action=APPROVE_RECEIPT, resourceType=IMPORT, resourceId=" + im.getId() + ", userId=" + uid + ", username=" + uname + ", code=" + im.getCode());
            sendActivityLog(uid, uname, "APPROVE_RECEIPT", "IMPORT", im.getId(), im.getCode(), "Approved import: " + im.getCode());
        } catch (Exception e) {
            logger.warn("Failed to send activity log for import approve: {}", e.getMessage());
        }

        return toDtoWithCalcTotal(im);
    }

    @Override
    @Transactional
    public SupplierImportDto confirm(Long id) {
        ShopImport im = importRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Import not found: " + id));

        if (im.getStatus() != ImportStatus.APPROVED) {
            throw new IllegalStateException("Chỉ có thể nhập kho khi phiếu đã được duyệt (APPROVED)");
        }

        im.setStatus(ImportStatus.IMPORTED);
        Long currentUserId = getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        if (currentUserId != null) {
            im.setImportedBy(currentUserId);
            im.setImportedAt(now);
        }
        im.setUpdatedAt(now);
        im = importRepo.save(im);
        try {
            Long uid = getCurrentUserId();
            String uname = getCurrentUsername();
            if (uname == null) uname = "system";
            System.out.println("Preparing to send activity log: action=APPROVE_RECEIPT, resourceType=IMPORT, resourceId=" + im.getId() + ", userId=" + uid + ", username=" + uname + ", code=" + im.getCode());
            sendActivityLog(uid, uname, "APPROVE_RECEIPT", "IMPORT", im.getId(), im.getCode(), "Confirmed import (imported): " + im.getCode());
        } catch (Exception e) {
            logger.warn("Failed to send activity log for import confirm: {}", e.getMessage());
        }

        // Cập nhật tồn kho vào shop_stocks
        List<ShopImportDetail> details = detailRepo.findByImportId(id);
        for (ShopImportDetail d : details) {
            if (d.getQuantity() != null && d.getQuantity() > 0 && d.getStoreId() != null) {
                // Lấy storeId từ detail (mỗi dòng có thể khác kho)
                Long storeId = d.getStoreId();
                // Tìm hoặc tạo stock record
                ShopStock stock = stockRepo.findByProductIdAndStoreId(d.getProductId(), storeId)
                        .orElseGet(() -> {
                            ShopStock newStock = new ShopStock();
                            newStock.setProductId(d.getProductId());
                            newStock.setStoreId(storeId);
                            newStock.setQuantity(0);
                            // Nếu sản phẩm chưa có trong kho, set mặc định minStock = 10 và maxStock = 1000
                            newStock.setMinStock(10);
                            newStock.setMaxStock(1000);
                            return stockRepo.save(newStock);
                        });

                // Tăng số lượng
                stock.setQuantity(stock.getQuantity() + d.getQuantity());
                stockRepo.save(stock);
            }
        }

        return toDtoWithCalcTotal(im);
    }

    @Override
    @Transactional
    public SupplierImportDto cancel(Long id) {
        ShopImport im = importRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Import not found: " + id));

        if (im.getStatus() != ImportStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể hủy phiếu đang ở trạng thái PENDING");
        }

        im.setStatus(ImportStatus.CANCELLED);
        im.setUpdatedAt(LocalDateTime.now());
        im = importRepo.save(im);
        try {
            Long uid = getCurrentUserId();
            String uname = getCurrentUsername();
            if (uname == null) uname = "system";
            System.out.println("Preparing to send activity log: action=CANCEL_RECEIPT, resourceType=IMPORT, resourceId=" + im.getId() + ", userId=" + uid + ", username=" + uname + ", code=" + im.getCode());
            sendActivityLog(uid, uname, "CANCEL_RECEIPT", "IMPORT", im.getId(), im.getCode(), "Cancelled import: " + im.getCode());
        } catch (Exception e) {
            logger.warn("Failed to send activity log for import cancel: {}", e.getMessage());
        }

        return toDtoWithCalcTotal(im);
    }

    @Override
    @Transactional
    public SupplierImportDto reject(Long id) {
        ShopImport im = importRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Import not found: " + id));

        if (im.getStatus() != ImportStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể từ chối phiếu đang ở trạng thái PENDING");
        }

        im.setStatus(ImportStatus.REJECTED);
        Long currentUserId = getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        if (currentUserId != null) {
            im.setRejectedBy(currentUserId);
            im.setRejectedAt(now);
        }
        im.setUpdatedAt(now);
        im = importRepo.save(im);
        try {
            Long uid = getCurrentUserId();
            String uname = getCurrentUsername();
            if (uname == null) uname = "system";
            System.out.println("Preparing to send activity log: action=CANCEL_RECEIPT, resourceType=IMPORT, resourceId=" + im.getId() + ", userId=" + uid + ", username=" + uname + ", code=" + im.getCode());
            sendActivityLog(uid, uname, "CANCEL_RECEIPT", "IMPORT", im.getId(), im.getCode(), "Rejected import: " + im.getCode());
        } catch (Exception e) {
            logger.warn("Failed to send activity log for import reject: {}", e.getMessage());
        }

        return toDtoWithCalcTotal(im);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierImportDto> getAll() {
        // Dùng pagination với limit để tránh load toàn bộ
        Page<ShopImport> importPage = importRepo.findAll(
                org.springframework.data.domain.PageRequest.of(0, 100)); // Limit to 100 records

        List<Long> importIds = importPage.getContent().stream()
                .map(ShopImport::getId)
                .filter(Objects::nonNull)
                .toList();

        // Batch fetch details và stores
        Map<Long, BigDecimal> totalsMap = new HashMap<>();
        Map<Long, List<ShopImportDetail>> detailsMap = new HashMap<>();
        Map<Long, com.example.inventory_service.entity.ShopStore> storeMap = new HashMap<>();

        if (!importIds.isEmpty()) {
            detailRepo.sumTotalsByImportIds(importIds).forEach(row -> {
                Long id = (Long) row[0];
                BigDecimal total = (BigDecimal) row[1];
                totalsMap.put(id, total);
            });

            List<ShopImportDetail> details = detailRepo.findByImportIdIn(importIds);
            detailsMap = details.stream().collect(Collectors.groupingBy(ShopImportDetail::getImportId));

            List<Long> storeIds = details.stream()
                    .map(ShopImportDetail::getStoreId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!storeIds.isEmpty()) {
                storeMap.putAll(storeRepo.findAllById(storeIds).stream()
                        .collect(Collectors.toMap(com.example.inventory_service.entity.ShopStore::getId, Function.identity())));
            }
        }

        final Map<Long, List<ShopImportDetail>> detailsMapFinal = detailsMap;
        final Map<Long, com.example.inventory_service.entity.ShopStore> storeMapFinal = storeMap;
        return importPage.getContent().stream()
                .map(im -> toDtoWithCalcTotal(
                        im,
                        detailsMapFinal.getOrDefault(im.getId(), List.of()),
                        totalsMap.get(im.getId()),
                        storeMapFinal))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierImportDto> getByStore(Long storeId) {
        // Dùng pagination với limit để tránh load quá nhiều records
        Page<ShopImport> importPage = importRepo.findByStoreId(
                storeId,
                org.springframework.data.domain.PageRequest.of(0, 100)); // Limit to 100 records

        List<Long> importIds = importPage.getContent().stream()
                .map(ShopImport::getId)
                .filter(Objects::nonNull)
                .toList();

        // Batch fetch details và stores
        Map<Long, BigDecimal> totalsMap = new HashMap<>();
        Map<Long, List<ShopImportDetail>> detailsMap = new HashMap<>();
        Map<Long, com.example.inventory_service.entity.ShopStore> storeMap = new HashMap<>();

        if (!importIds.isEmpty()) {
            detailRepo.sumTotalsByImportIds(importIds).forEach(row -> {
                Long id = (Long) row[0];
                BigDecimal total = (BigDecimal) row[1];
                totalsMap.put(id, total);
            });

            List<ShopImportDetail> details = detailRepo.findByImportIdIn(importIds);
            detailsMap = details.stream().collect(Collectors.groupingBy(ShopImportDetail::getImportId));

            List<Long> storeIds = details.stream()
                    .map(ShopImportDetail::getStoreId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!storeIds.isEmpty()) {
                storeMap.putAll(storeRepo.findAllById(storeIds).stream()
                        .collect(Collectors.toMap(com.example.inventory_service.entity.ShopStore::getId, Function.identity())));
            }
        }

        final Map<Long, List<ShopImportDetail>> detailsMapFinal = detailsMap;
        final Map<Long, com.example.inventory_service.entity.ShopStore> storeMapFinal = storeMap;
        return importPage.getContent().stream()
                .map(im -> toDtoWithCalcTotal(
                        im,
                        detailsMapFinal.getOrDefault(im.getId(), List.of()),
                        totalsMap.get(im.getId()),
                        storeMapFinal))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierImportDto> getAll(Pageable pageable) {
        // Dùng pagination để tránh load toàn bộ
        Page<ShopImport> importPage = importRepo.findAll(pageable);

        List<Long> importIds = importPage.getContent().stream()
                .map(ShopImport::getId)
                .filter(Objects::nonNull)
                .toList();

        // Batch fetch details và stores
        Map<Long, BigDecimal> totalsMap = new HashMap<>();
        Map<Long, List<ShopImportDetail>> detailsMap = new HashMap<>();
        Map<Long, com.example.inventory_service.entity.ShopStore> storeMap = new HashMap<>();

        if (!importIds.isEmpty()) {
            detailRepo.sumTotalsByImportIds(importIds).forEach(row -> {
                Long id = (Long) row[0];
                BigDecimal total = (BigDecimal) row[1];
                totalsMap.put(id, total);
            });

            List<ShopImportDetail> details = detailRepo.findByImportIdIn(importIds);
            detailsMap = details.stream().collect(Collectors.groupingBy(ShopImportDetail::getImportId));

            List<Long> storeIds = details.stream()
                    .map(ShopImportDetail::getStoreId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!storeIds.isEmpty()) {
                storeMap.putAll(storeRepo.findAllById(storeIds).stream()
                        .collect(Collectors.toMap(com.example.inventory_service.entity.ShopStore::getId, Function.identity())));
            }
        }

        final Map<Long, List<ShopImportDetail>> detailsMapFinal = detailsMap;
        final Map<Long, com.example.inventory_service.entity.ShopStore> storeMapFinal = storeMap;
        List<SupplierImportDto> dtoPage = importPage.getContent().stream()
                .map(im -> toDtoWithCalcTotal(
                        im,
                        detailsMapFinal.getOrDefault(im.getId(), List.of()),
                        totalsMap.get(im.getId()),
                        storeMapFinal))
                .toList();

        return new PageImpl<>(dtoPage, pageable, importPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierImportDto> getByStore(Long storeId, Pageable pageable) {
        // Dùng pagination để tránh load quá nhiều records
        Page<ShopImport> importPage = importRepo.findByStoreId(storeId, pageable);

        List<Long> importIds = importPage.getContent().stream()
                .map(ShopImport::getId)
                .filter(Objects::nonNull)
                .toList();

        // Batch fetch details và stores
        Map<Long, BigDecimal> totalsMap = new HashMap<>();
        Map<Long, List<ShopImportDetail>> detailsMap = new HashMap<>();
        Map<Long, com.example.inventory_service.entity.ShopStore> storeMap = new HashMap<>();

        if (!importIds.isEmpty()) {
            detailRepo.sumTotalsByImportIds(importIds).forEach(row -> {
                Long id = (Long) row[0];
                BigDecimal total = (BigDecimal) row[1];
                totalsMap.put(id, total);
            });

            List<ShopImportDetail> details = detailRepo.findByImportIdIn(importIds);
            detailsMap = details.stream().collect(Collectors.groupingBy(ShopImportDetail::getImportId));

            List<Long> storeIds = details.stream()
                    .map(ShopImportDetail::getStoreId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!storeIds.isEmpty()) {
                storeMap.putAll(storeRepo.findAllById(storeIds).stream()
                        .collect(Collectors.toMap(com.example.inventory_service.entity.ShopStore::getId, Function.identity())));
            }
        }

        final Map<Long, List<ShopImportDetail>> detailsMapFinal = detailsMap;
        final Map<Long, com.example.inventory_service.entity.ShopStore> storeMapFinal = storeMap;
        List<SupplierImportDto> dtoPage = importPage.getContent().stream()
                .map(im -> toDtoWithCalcTotal(
                        im,
                        detailsMapFinal.getOrDefault(im.getId(), List.of()),
                        totalsMap.get(im.getId()),
                        storeMapFinal))
                .toList();

        return new PageImpl<>(dtoPage, pageable, importPage.getTotalElements());
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
     * Lấy tên đầy đủ của user từ username
     */
    @SuppressWarnings("unused")
    private String getUserFullName(String username) {
        try {
            return userRepo.findFullNameByUsername(username)
                    .map(name -> name.trim())
                    .filter(name -> !name.isEmpty())
                    .orElse(username);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to get user full name: " + e.getMessage());
            return username;
        }
    }
    
    /**
     * Lấy username hiện tại từ SecurityContext
     */
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

    private String limitNote(String note) {
        if (note == null)
            return null;
        int max = 255;
        return note.length() > max ? note.substring(0, max) : note;
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

    /**
     * Gửi activity log tới auth-service bằng Java 11 HttpClient.
     */
    private void sendActivityLog(Long userId, String username, String action, String resourceType, Long resourceId, String resourceName, String details) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("username", username);
            // include displayName if available (try to resolve full name), fallback to username in controller
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
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            String authUrl = System.getenv("AUTH_SERVICE_URL");
            if (authUrl == null || authUrl.isBlank()) {
                authUrl = "http://localhost:8080";
            }
            // Use internal endpoint and include internal token header
            String endpoint = authUrl.endsWith("/") ? authUrl + "api/internal/activity-logs" : authUrl + "/api/internal/activity-logs";
            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(endpoint))
                    .header("Content-Type", "application/json");
            String token = System.getenv("ACTIVITY_LOG_SERVICE_TOKEN");
            if (token != null && !token.isBlank()) {
                builder.header("X-Activity-Log-Token", token);
            }
            java.net.http.HttpRequest req = builder.POST(java.net.http.HttpRequest.BodyPublishers.ofString(json)).build();
            System.out.println("Sending activity log to: " + endpoint + " payload: " + json + " tokenPresent=" + (token != null && !token.isBlank()));
            client.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.ofString())
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

    private SupplierImportDto toDtoWithCalcTotal(ShopImport im) {
        List<ShopImportDetail> details = detailRepo.findByImportId(im.getId());
        return toDtoWithCalcTotal(im, details, null, null);
    }

    private SupplierImportDto toDtoWithCalcTotal(
            ShopImport im,
            List<ShopImportDetail> details,
            BigDecimal precomputedTotal,
            Map<Long, com.example.inventory_service.entity.ShopStore> storeMap) {
        BigDecimal total = precomputedTotal != null ? precomputedTotal : BigDecimal.ZERO;
        List<ImportDetailDto> itemDtos = new ArrayList<>();

        if (details != null) {
            for (ShopImportDetail d : details) {
                if (precomputedTotal == null) { // Only calculate if not precomputed
                    if (d.getUnitPrice() != null && d.getQuantity() != null) {
                BigDecimal line = d.getUnitPrice().multiply(BigDecimal.valueOf(d.getQuantity()));

                // Áp dụng chiết khấu nếu có
                if (d.getDiscountPercent() != null && d.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal discountMultiplier = BigDecimal.ONE
                            .subtract(d.getDiscountPercent().divide(BigDecimal.valueOf(100), 4,
                                    java.math.RoundingMode.HALF_UP));
                    line = line.multiply(discountMultiplier);
                }

                total = total.add(line);
                    }
                }

                ImportDetailDto itemDto = new ImportDetailDto();
                itemDto.setId(d.getId());
                itemDto.setProductId(d.getProductId());
                itemDto.setStoreId(d.getStoreId());
                itemDto.setQuantity(d.getQuantity());
                itemDto.setUnitPrice(d.getUnitPrice());
                itemDto.setDiscountPercent(d.getDiscountPercent());
                itemDto.setProductCode(null);
                itemDto.setProductName(null);
                itemDto.setUnit(null);
                itemDto.setStoreName(null);
                itemDto.setStoreCode(null);

                // Lấy thông tin kho từ map nếu có
                if (d.getStoreId() != null && storeMap != null && !storeMap.isEmpty()) {
                    com.example.inventory_service.entity.ShopStore store = storeMap.get(d.getStoreId());
                    if (store != null) {
                        itemDto.setStoreName(store.getName());
                        itemDto.setStoreCode(store.getCode());
                    }
                } else if (d.getStoreId() != null) {
                    // Fallback: query từ DB nếu không có trong map
                    storeRepo.findById(d.getStoreId()).ifPresent(store -> {
                        itemDto.setStoreName(store.getName());
                        itemDto.setStoreCode(store.getCode());
                    });
                }

                itemDtos.add(itemDto);
            }
        }

        SupplierImportDto dto = toDto(im, total, storeMap);
        dto.setItems(itemDtos);
        return dto;
    }

    private SupplierImportDto toDto(ShopImport imp, BigDecimal total) {
        return toDto(imp, total, null);
    }

    private SupplierImportDto toDto(ShopImport imp, BigDecimal total, Map<Long, com.example.inventory_service.entity.ShopStore> storeMap) {
        SupplierImportDto dto = new SupplierImportDto();
        dto.setId(imp.getId());
        dto.setCode(imp.getCode());
        dto.setStoreId(imp.getStoreId());
        dto.setSupplierId(imp.getSupplierId());
        dto.setStatus(imp.getStatus() != null ? imp.getStatus().name() : null);
        dto.setImportsDate(imp.getImportsDate() != null ? 
            java.sql.Timestamp.valueOf(imp.getImportsDate()) : null);
        dto.setNote(imp.getNote());
        dto.setTotalValue(total);

        // Lấy thông tin kho đích từ map nếu có, hoặc query từ DB
        if (imp.getStoreId() != null) {
            if (storeMap != null && !storeMap.isEmpty()) {
                com.example.inventory_service.entity.ShopStore store = storeMap.get(imp.getStoreId());
                if (store != null) {
                    dto.setStoreName(store.getName());
                    dto.setStoreCode(store.getCode());
                }
            } else {
            storeRepo.findById(imp.getStoreId()).ifPresent(store -> {
                dto.setStoreName(store.getName());
                dto.setStoreCode(store.getCode());
            });
            }
        }

        // Phiếu nhập chỉ làm việc với NCC
        if (imp.getSupplierId() != null) {
            try {
                var supplierInfo = productClient.getSupplier(imp.getSupplierId());
                if (supplierInfo != null) {
                    dto.setSupplierName(supplierInfo.getName());
                    dto.setSupplierCode(supplierInfo.getCode());
                    dto.setSupplierPhone(supplierInfo.getPhone());
                    dto.setSupplierAddress(supplierInfo.getAddress());
                }
            } catch (Exception ex) {
                System.err.println("❌ Failed to get supplier info for supplierId: " + imp.getSupplierId());
                dto.setSupplierName(null);
            }
        }

        // Map ảnh
        List<String> images = new ArrayList<>();
        String raw = imp.getAttachmentImage();
        if (raw != null && !raw.isBlank()) {
            images = Arrays.stream(raw.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        dto.setAttachmentImages(images);

        // Map audit fields với userId và timestamp - convert LocalDateTime to Timestamp
        dto.setCreatedBy(imp.getCreatedBy());
        dto.setCreatedAt(imp.getCreatedAt() != null ? 
            java.sql.Timestamp.valueOf(imp.getCreatedAt()) : null);
        dto.setApprovedBy(imp.getApprovedBy());
        dto.setApprovedAt(imp.getApprovedAt() != null ? 
            java.sql.Timestamp.valueOf(imp.getApprovedAt()) : null);
        dto.setRejectedBy(imp.getRejectedBy());
        dto.setRejectedAt(imp.getRejectedAt() != null ? 
            java.sql.Timestamp.valueOf(imp.getRejectedAt()) : null);
        dto.setImportedBy(imp.getImportedBy());
        dto.setImportedAt(imp.getImportedAt() != null ? 
            java.sql.Timestamp.valueOf(imp.getImportedAt()) : null);
        
        // Lấy tên user và role từ userId
        try {
            if (userRepo == null) {
                System.err.println("⚠️ userRepo is null, cannot fetch user names");
            } else {
                if (imp.getCreatedBy() != null) {
                    String createdByUsername = getUserFullNameFromId(imp.getCreatedBy());
                    if (createdByUsername != null && !createdByUsername.trim().isEmpty()) {
                        dto.setCreatedByName(createdByUsername);
                        System.out.println("✅ Set createdByName: " + createdByUsername + " for userId: " + imp.getCreatedBy());
                    } else {
                        System.out.println("⚠️ Could not get name for createdBy userId: " + imp.getCreatedBy());
                    }
                    String createdByRole = getUserRoleFromId(imp.getCreatedBy());
                    if (createdByRole != null && !createdByRole.trim().isEmpty()) {
                        dto.setCreatedByRole(createdByRole);
                        System.out.println("✅ Set createdByRole: " + createdByRole + " for userId: " + imp.getCreatedBy());
                    } else {
                        System.out.println("⚠️ Could not get role for createdBy userId: " + imp.getCreatedBy());
                    }
                }
                if (imp.getApprovedBy() != null) {
                    String approvedByUsername = getUserFullNameFromId(imp.getApprovedBy());
                    if (approvedByUsername != null && !approvedByUsername.trim().isEmpty()) {
                        dto.setApprovedByName(approvedByUsername);
                        System.out.println("✅ Set approvedByName: " + approvedByUsername + " for userId: " + imp.getApprovedBy());
                    } else {
                        System.out.println("⚠️ Could not get name for approvedBy userId: " + imp.getApprovedBy());
                    }
                    String approvedByRole = getUserRoleFromId(imp.getApprovedBy());
                    if (approvedByRole != null && !approvedByRole.trim().isEmpty()) {
                        dto.setApprovedByRole(approvedByRole);
                        System.out.println("✅ Set approvedByRole: " + approvedByRole + " for userId: " + imp.getApprovedBy());
                    } else {
                        System.out.println("⚠️ Could not get role for approvedBy userId: " + imp.getApprovedBy());
                    }
                }
                if (imp.getRejectedBy() != null) {
                    String rejectedByUsername = getUserFullNameFromId(imp.getRejectedBy());
                    if (rejectedByUsername != null && !rejectedByUsername.trim().isEmpty()) {
                        dto.setRejectedByName(rejectedByUsername);
                        System.out.println("✅ Set rejectedByName: " + rejectedByUsername + " for userId: " + imp.getRejectedBy());
                    } else {
                        System.out.println("⚠️ Could not get name for rejectedBy userId: " + imp.getRejectedBy());
                    }
                    String rejectedByRole = getUserRoleFromId(imp.getRejectedBy());
                    if (rejectedByRole != null && !rejectedByRole.trim().isEmpty()) {
                        dto.setRejectedByRole(rejectedByRole);
                        System.out.println("✅ Set rejectedByRole: " + rejectedByRole + " for userId: " + imp.getRejectedBy());
                    } else {
                        System.out.println("⚠️ Could not get role for rejectedBy userId: " + imp.getRejectedBy());
                    }
                }
                if (imp.getImportedBy() != null) {
                    String importedByUsername = getUserFullNameFromId(imp.getImportedBy());
                    if (importedByUsername != null && !importedByUsername.trim().isEmpty()) {
                        dto.setImportedByName(importedByUsername);
                        System.out.println("✅ Set importedByName: " + importedByUsername + " for userId: " + imp.getImportedBy());
                    } else {
                        System.out.println("⚠️ Could not get name for importedBy userId: " + imp.getImportedBy());
                    }
                    String importedByRole = getUserRoleFromId(imp.getImportedBy());
                    if (importedByRole != null && !importedByRole.trim().isEmpty()) {
                        dto.setImportedByRole(importedByRole);
                        System.out.println("✅ Set importedByRole: " + importedByRole + " for userId: " + imp.getImportedBy());
                    } else {
                        System.out.println("⚠️ Could not get role for importedBy userId: " + imp.getImportedBy());
                    }
                }
            }
        } catch (Exception e) {
            // Nếu có lỗi khi lấy user name, bỏ qua và tiếp tục
            System.err.println("⚠️ Failed to get user names: " + e.getMessage());
            e.printStackTrace();
        }

        return dto;
    }
    
    /**
     * Lấy fullName từ userId bằng cách query database
     */
    private String getUserFullNameFromId(Long userId) {
        try {
            if (userRepo == null) {
                return null;
            }
            Optional<String> fullName = userRepo.findFullNameByUserId(userId);
            if (fullName.isPresent() && !fullName.get().trim().isEmpty()) {
                return fullName.get().trim();
            }
            // Nếu không có fullName, lấy username
            Optional<String> username = userRepo.findUsernameByUserId(userId);
            return username.orElse(null);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to get user full name from userId " + userId + ": " + e.getMessage());
            return null;
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
            Optional<String> role = userRepo.findRoleByUserId(userId);
            return role.orElse(null);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to get user role from userId " + userId + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ShopImport importEntity = importRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Phiếu nhập không tồn tại với id: " + id));

        // Lấy role của user hiện tại
        Long currentUserId = getCurrentUserId();
        String userRole = getUserRoleFromId(currentUserId);

        // Kiểm tra quyền: chỉ ADMIN và MANAGER mới được xóa
        if (userRole == null || (!userRole.equals("ADMIN") && !userRole.equals("MANAGER"))) {
            throw new com.example.inventory_service.exception.BadRequestException("Chỉ ADMIN và MANAGER mới có quyền xóa phiếu nhập");
        }

        // Nếu là ADMIN: xóa trực tiếp
        if ("ADMIN".equals(userRole)) {
            // Xóa chi tiết trước
            detailRepo.deleteByImportId(id);
            // Xóa phiếu nhập
            importRepo.delete(importEntity);
            logger.info("Admin đã xóa phiếu nhập id: {}", id);
            return;
        }

        // Nếu là MANAGER: đánh dấu status = CANCELLED và lưu note "DELETE_REQUESTED"
        // ADMIN sẽ cần duyệt để xóa thực sự
        if ("MANAGER".equals(userRole)) {
            importEntity.setStatus(ImportStatus.CANCELLED);
            String currentNote = importEntity.getNote() != null ? importEntity.getNote() : "";
            String deleteRequestNote = "[DELETE_REQUESTED_BY_MANAGER] " + getCurrentUsername() + " đã yêu cầu xóa phiếu này. Cần ADMIN duyệt để xóa thực sự.";
            importEntity.setNote(currentNote.isEmpty() ? deleteRequestNote : currentNote + "\n" + deleteRequestNote);
            importEntity.setUpdatedAt(LocalDateTime.now());
            importRepo.save(importEntity);
            logger.info("Manager đã yêu cầu xóa phiếu nhập id: {}, cần ADMIN duyệt", id);
            throw new com.example.inventory_service.exception.BadRequestException("Đã gửi yêu cầu xóa. ADMIN cần duyệt để xóa thực sự.");
        }
    }
}
