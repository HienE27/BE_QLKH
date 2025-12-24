package com.example.inventory_service.service.impl;

import com.example.inventory_service.client.ProductServiceClient;
import com.example.inventory_service.dto.*;
import com.example.inventory_service.entity.InventoryCheck;
import com.example.inventory_service.entity.InventoryCheckDetail;
import com.example.inventory_service.exception.NotFoundException;
import com.example.inventory_service.repository.InventoryCheckDetailRepository;
import com.example.inventory_service.repository.InventoryCheckRepository;
import com.example.inventory_service.service.InventoryCheckService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
public class InventoryCheckServiceImpl implements InventoryCheckService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryCheckServiceImpl.class);

    private final InventoryCheckRepository checkRepo;
    private final InventoryCheckDetailRepository detailRepo;
    private final ProductServiceClient productClient;
    private final com.example.inventory_service.repository.ShopStoreRepository storeRepo;
    private final com.example.inventory_service.repository.ShopStockRepository stockRepo;
    private com.example.inventory_service.repository.UserQueryRepository userRepo;

    public InventoryCheckServiceImpl(
            InventoryCheckRepository checkRepo,
            InventoryCheckDetailRepository detailRepo,
            ProductServiceClient productClient,
            com.example.inventory_service.repository.ShopStoreRepository storeRepo,
            com.example.inventory_service.repository.ShopStockRepository stockRepo,
            com.example.inventory_service.repository.UserQueryRepository userRepo) {
        this.checkRepo = checkRepo;
        this.detailRepo = detailRepo;
        this.productClient = productClient;
        this.storeRepo = storeRepo;
        this.stockRepo = stockRepo;
        this.userRepo = userRepo;
    }

    @Override
    @Transactional
    public InventoryCheckDto create(InventoryCheckRequest request) {
        Date now = new Date();

        InventoryCheck check = new InventoryCheck();

        if (request.getCheckCode() != null && !request.getCheckCode().isBlank()) {
            check.setCheckCode(request.getCheckCode());
        } else {
            check.setCheckCode(generateCode());
        }

        check.setStoreId(request.getStoreId());
        check.setDescription(request.getDescription());
        check.setStatus("PENDING");
        // Nếu checkDate chỉ có date (không có time), set thời gian là thời gian hiện
        // tại
        Date checkDateValue = request.getCheckDate();
        if (checkDateValue != null) {
            // Kiểm tra xem checkDate có chỉ là date (00:00:00) không
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(checkDateValue);
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            int minute = cal.get(java.util.Calendar.MINUTE);
            int second = cal.get(java.util.Calendar.SECOND);

            // Nếu là 00:00:00, có thể là do frontend chỉ gửi date, set thời gian hiện tại
            if (hour == 0 && minute == 0 && second == 0) {
                java.util.Calendar nowCal = java.util.Calendar.getInstance();
                nowCal.setTime(now);
                cal.set(java.util.Calendar.HOUR_OF_DAY, nowCal.get(java.util.Calendar.HOUR_OF_DAY));
                cal.set(java.util.Calendar.MINUTE, nowCal.get(java.util.Calendar.MINUTE));
                cal.set(java.util.Calendar.SECOND, nowCal.get(java.util.Calendar.SECOND));
                checkDateValue = cal.getTime();
            }
            check.setCheckDate(checkDateValue);
        } else {
            check.setCheckDate(now);
        }
        check.setNote(request.getNote());
        Long currentUserId = getCurrentUserId();
        System.out.println("🔍 Creating inventory check - currentUserId: " + currentUserId);
        if (currentUserId != null) {
            check.setCreatedBy(currentUserId);
            System.out.println("✅ Set createdBy: " + currentUserId);
        } else {
            System.err.println("⚠️ Warning: currentUserId is null when creating inventory check");
        }
        check.setCreatedAt(now);
        check.setUpdatedAt(now);

        // Lưu ảnh
        if (request.getAttachmentImages() != null && !request.getAttachmentImages().isEmpty()) {
            String joined = request.getAttachmentImages().stream()
                    .map(this::normalizeImagePath)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(";"));
            check.setAttachmentImage(joined);
        }

        check = checkRepo.save(check);

        // Lưu chi tiết
        BigDecimal totalDiff = BigDecimal.ZERO;
        List<InventoryCheckDetail> details = new ArrayList<>();

        if (request.getItems() != null) {
            for (InventoryCheckDetailRequest item : request.getItems()) {
                if (item.getSystemQuantity() == null || item.getActualQuantity() == null)
                    continue;

                InventoryCheckDetail d = new InventoryCheckDetail();
                d.setInventoryCheckId(check.getId());
                d.setProductId(item.getProductId());
                d.setSystemQuantity(item.getSystemQuantity());
                d.setActualQuantity(item.getActualQuantity());

                // Tính chênh lệch
                int diff = item.getActualQuantity() - item.getSystemQuantity();
                d.setDifferenceQuantity(diff);

                d.setUnitPrice(item.getUnitPrice());
                d.setNote(item.getNote());

                // Tính giá trị chênh lệch
                if (item.getUnitPrice() != null) {
                    BigDecimal value = item.getUnitPrice().multiply(BigDecimal.valueOf(diff));
                    d.setTotalValue(value);
                    totalDiff = totalDiff.add(value);
                }

                details.add(d);
            }
        }

        if (!details.isEmpty()) {
            detailRepo.saveAll(details);
        }
        // Send activity log for create
        try {
            String uname = getCurrentUsername();
            if (uname == null) uname = getUserFullNameFromId(check.getCreatedBy());
            if (uname == null) uname = "system";
            sendActivityLog(
                    check.getCreatedBy(),
                    uname,
                    "CREATE_INVENTORY_CHECK",
                    "INVENTORY_CHECK",
                    check.getId(),
                    check.getCheckCode(),
                    "Created inventory check: " + check.getCheckCode()
            );
        } catch (Exception e) {
            logger.warn("Failed to send activity log for inventory check create: {}", e.getMessage());
        }

        return toDto(check, totalDiff);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryCheckDto> search(
            String status,
            String checkCode,
            LocalDate from,
            LocalDate to) {
        Date fromDate = from != null ? java.sql.Date.valueOf(from) : null;
        Date toDate = to != null ? java.sql.Date.valueOf(to.plusDays(1)) : null;

        // Sử dụng pagination với limit để tránh load toàn bộ
        Page<InventoryCheck> page = checkRepo.searchInventoryChecksPaged(
                status,
                checkCode,
                fromDate,
                toDate,
                org.springframework.data.domain.PageRequest.of(0, 1000)); // Limit to 1000 records

        List<Long> checkIds = page.getContent().stream()
                .map(InventoryCheck::getId)
                .filter(java.util.Objects::nonNull)
                .toList();

        // Batch fetch details để tránh N+1
        Map<Long, List<InventoryCheckDetail>> detailsMap = new HashMap<>();
        if (!checkIds.isEmpty()) {
            List<InventoryCheckDetail> allDetails = detailRepo.findByInventoryCheckIdIn(checkIds);
            detailsMap = allDetails.stream()
                    .collect(Collectors.groupingBy(InventoryCheckDetail::getInventoryCheckId));
        }

        // Batch fetch stores
        Map<Long, com.example.inventory_service.entity.ShopStore> storeMap = new HashMap<>();
        List<Long> storeIds = page.getContent().stream()
                .map(InventoryCheck::getStoreId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (!storeIds.isEmpty()) {
            storeMap.putAll(storeRepo.findAllById(storeIds).stream()
                    .collect(Collectors.toMap(com.example.inventory_service.entity.ShopStore::getId,
                            java.util.function.Function.identity())));
        }

        final Map<Long, List<InventoryCheckDetail>> detailsMapFinal = detailsMap;
        final Map<Long, com.example.inventory_service.entity.ShopStore> storeMapFinal = storeMap;
        return page.getContent().stream()
                .map(check -> toDtoWithCalcTotal(check, detailsMapFinal.getOrDefault(check.getId(), List.of()),
                        storeMapFinal))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InventoryCheckDto> searchPaged(
            String status,
            String checkCode,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {
        long startTime = System.currentTimeMillis();
        Date fromDate = from != null ? java.sql.Date.valueOf(from) : null;
        Date toDate = to != null ? java.sql.Date.valueOf(to.plusDays(1)) : null;

        // Dùng pagination trực tiếp từ repository
        Page<InventoryCheck> checkPage = checkRepo.searchInventoryChecksPaged(
                status,
                checkCode,
                fromDate,
                toDate,
                pageable);

        List<Long> checkIds = checkPage.getContent().stream()
                .map(InventoryCheck::getId)
                .filter(java.util.Objects::nonNull)
                .toList();

        // Batch fetch details để tránh N+1
        Map<Long, List<InventoryCheckDetail>> detailsMap = new HashMap<>();
        if (!checkIds.isEmpty()) {
            List<InventoryCheckDetail> allDetails = detailRepo.findByInventoryCheckIdIn(checkIds);
            detailsMap = allDetails.stream()
                    .collect(Collectors.groupingBy(InventoryCheckDetail::getInventoryCheckId));
        }

        // Batch fetch stores
        Map<Long, com.example.inventory_service.entity.ShopStore> storeMap = new HashMap<>();
        List<Long> storeIds = checkPage.getContent().stream()
                .map(InventoryCheck::getStoreId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (!storeIds.isEmpty()) {
            storeMap.putAll(storeRepo.findAllById(storeIds).stream()
                    .collect(Collectors.toMap(com.example.inventory_service.entity.ShopStore::getId,
                            java.util.function.Function.identity())));
        }

        final Map<Long, List<InventoryCheckDetail>> detailsMapFinal = detailsMap;
        final Map<Long, com.example.inventory_service.entity.ShopStore> storeMapFinal = storeMap;
        List<InventoryCheckDto> dtoPage = checkPage.getContent().stream()
                .map(check -> toDtoWithCalcTotal(check, detailsMapFinal.getOrDefault(check.getId(), List.of()),
                        storeMapFinal))
                .toList();

        logger.debug("Search paged query took {}ms, processed {} records",
                System.currentTimeMillis() - startTime, checkPage.getTotalElements());
        return new PageImpl<>(dtoPage, pageable, checkPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryCheckDto getById(Long id) {
        InventoryCheck check = checkRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Inventory check not found: " + id));

        return toDtoWithCalcTotal(check);
    }

    @Override
    @Transactional
    public InventoryCheckDto update(Long id, InventoryCheckRequest request) {
        InventoryCheck check = checkRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Inventory check not found: " + id));

        if (!"PENDING".equals(check.getStatus())) {
            throw new IllegalStateException("Chỉ có thể cập nhật phiếu đang ở trạng thái PENDING");
        }

        if (request.getCheckCode() != null && !request.getCheckCode().isBlank()) {
            check.setCheckCode(request.getCheckCode());
        }
        check.setStoreId(request.getStoreId());
        check.setDescription(request.getDescription());
        check.setCheckDate(request.getCheckDate() != null ? request.getCheckDate() : check.getCheckDate());
        check.setNote(request.getNote());
        check.setUpdatedAt(new Date());

        // Cập nhật ảnh
        if (request.getAttachmentImages() != null && !request.getAttachmentImages().isEmpty()) {
            String joined = request.getAttachmentImages().stream()
                    .map(this::normalizeImagePath)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(";"));
            check.setAttachmentImage(joined);
        } else {
            check.setAttachmentImage(null);
        }

        check = checkRepo.save(check);

        // Xóa chi tiết cũ và tạo mới
        detailRepo.deleteByInventoryCheckId(id);

        BigDecimal totalDiff = BigDecimal.ZERO;
        List<InventoryCheckDetail> details = new ArrayList<>();

        if (request.getItems() != null) {
            for (InventoryCheckDetailRequest item : request.getItems()) {
                if (item.getSystemQuantity() == null || item.getActualQuantity() == null)
                    continue;

                InventoryCheckDetail d = new InventoryCheckDetail();
                d.setInventoryCheckId(check.getId());
                d.setProductId(item.getProductId());
                d.setSystemQuantity(item.getSystemQuantity());
                d.setActualQuantity(item.getActualQuantity());

                int diff = item.getActualQuantity() - item.getSystemQuantity();
                d.setDifferenceQuantity(diff);

                d.setUnitPrice(item.getUnitPrice());
                d.setNote(item.getNote());

                if (item.getUnitPrice() != null) {
                    BigDecimal value = item.getUnitPrice().multiply(BigDecimal.valueOf(diff));
                    d.setTotalValue(value);
                    totalDiff = totalDiff.add(value);
                }

                details.add(d);
            }
        }

        if (!details.isEmpty()) {
            detailRepo.saveAll(details);
        }

        return toDto(check, totalDiff);
    }

    @Override
    @Transactional
    public InventoryCheckDto approve(Long id) {
        InventoryCheck check = checkRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Inventory check not found: " + id));

        if (!"PENDING".equals(check.getStatus())) {
            throw new IllegalStateException("Chỉ có thể duyệt phiếu đang ở trạng thái PENDING");
        }

        check.setStatus("APPROVED");
        Long currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            check.setApprovedBy(currentUserId);
        }
        check.setApprovedAt(new Date());
        check.setUpdatedAt(new Date());
        check = checkRepo.save(check);

        // Cập nhật tồn kho ngay khi duyệt
        updateStockFromInventoryCheck(check.getId(), check.getStoreId());

        // Send activity log for approve
        try {
            String uname = getCurrentUsername();
            if (uname == null) uname = getUserFullNameFromId(check.getApprovedBy());
            if (uname == null) uname = "system";
            sendActivityLog(
                    check.getApprovedBy(),
                    uname,
                    "APPROVE_INVENTORY_CHECK",
                    "INVENTORY_CHECK",
                    check.getId(),
                    check.getCheckCode(),
                    "Approved inventory check: " + check.getCheckCode()
            );
        } catch (Exception e) {
            logger.warn("Failed to send activity log for inventory check approve: {}", e.getMessage());
        }

        return toDtoWithCalcTotal(check);
    }

    @Override
    @Transactional
    public InventoryCheckDto confirm(Long id) {
        InventoryCheck check = checkRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Inventory check not found: " + id));

        if (!"APPROVED".equals(check.getStatus())) {
            throw new IllegalStateException("Chỉ có thể xác nhận phiếu đã được duyệt (APPROVED)");
        }

        Long currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            check.setConfirmedBy(currentUserId);
        }
        check.setConfirmedAt(new Date());
        check.setUpdatedAt(new Date());
        check = checkRepo.save(check);

        // Cập nhật tồn kho theo chênh lệch - cập nhật trực tiếp vào shop_stocks
        updateStockFromInventoryCheck(check.getId(), check.getStoreId());

        // Send activity log for confirm
        try {
            String uname = getCurrentUsername();
            if (uname == null) uname = getUserFullNameFromId(check.getConfirmedBy());
            if (uname == null) uname = "system";
            sendActivityLog(
                    check.getConfirmedBy(),
                    uname,
                    "CONFIRM_INVENTORY_CHECK",
                    "INVENTORY_CHECK",
                    check.getId(),
                    check.getCheckCode(),
                    "Confirmed inventory check: " + check.getCheckCode()
            );
        } catch (Exception e) {
            logger.warn("Failed to send activity log for inventory check confirm: {}", e.getMessage());
        }

        return toDtoWithCalcTotal(check);
    }

    @Override
    @Transactional
    public InventoryCheckDto reject(Long id, String reason) {
        InventoryCheck check = checkRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Inventory check not found: " + id));

        if (!"PENDING".equals(check.getStatus())) {
            throw new IllegalStateException("Chỉ có thể từ chối phiếu đang ở trạng thái PENDING");
        }

        check.setStatus("REJECTED");
        check.setNote(reason != null ? reason : check.getNote());
        Long currentUserId = getCurrentUserId();
        System.out.println("🔍 Rejecting inventory check - currentUserId: " + currentUserId);
        if (currentUserId != null) {
            check.setRejectedBy(currentUserId);
            System.out.println("✅ Set rejectedBy: " + currentUserId);
        } else {
            System.err.println("⚠️ Warning: currentUserId is null when rejecting inventory check");
        }
        check.setRejectedAt(new Date());
        System.out.println("✅ Set rejectedAt: " + check.getRejectedAt());
        check.setUpdatedAt(new Date());
        check = checkRepo.save(check);

        // Send activity log for reject
        try {
            String uname = getCurrentUsername();
            if (uname == null) uname = getUserFullNameFromId(check.getRejectedBy());
            if (uname == null) uname = "system";
            sendActivityLog(
                    check.getRejectedBy(),
                    uname,
                    "REJECT_INVENTORY_CHECK",
                    "INVENTORY_CHECK",
                    check.getId(),
                    check.getCheckCode(),
                    "Rejected inventory check: " + check.getCheckCode()
            );
        } catch (Exception e) {
            logger.warn("Failed to send activity log for inventory check reject: {}", e.getMessage());
        }

        return toDtoWithCalcTotal(check);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        InventoryCheck check = checkRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Inventory check not found: " + id));

        if (!"PENDING".equals(check.getStatus())) {
            throw new IllegalStateException("Chỉ có thể xóa phiếu đang ở trạng thái PENDING");
        }

        detailRepo.deleteByInventoryCheckId(id);
        checkRepo.delete(check);
    }

    // ========= HELPER METHODS ========= //

    private String generateCode() {
        return "BKK" + System.currentTimeMillis();
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
     * Cập nhật tồn kho từ inventory check details
     */
    private void updateStockFromInventoryCheck(Long inventoryCheckId, Long storeId) {
        if (storeId == null) {
            throw new IllegalStateException("Phiếu kiểm kê không có kho hàng");
        }

        List<InventoryCheckDetail> details = detailRepo.findByInventoryCheckId(inventoryCheckId);

        for (InventoryCheckDetail d : details) {
            if (d.getDifferenceQuantity() != null && d.getDifferenceQuantity() != 0) {
                Long productId = d.getProductId();
                Integer diffQty = d.getDifferenceQuantity();

                // Tìm hoặc tạo stock record
                com.example.inventory_service.entity.ShopStock stock = stockRepo
                        .findByProductIdAndStoreId(productId, storeId)
                        .orElseGet(() -> {
                            com.example.inventory_service.entity.ShopStock newStock = new com.example.inventory_service.entity.ShopStock();
                            newStock.setProductId(productId);
                            newStock.setStoreId(storeId);
                            newStock.setQuantity(0);
                            newStock.setMinStock(0);
                            newStock.setMaxStock(999999);
                            return stockRepo.save(newStock);
                        });

                // Cập nhật số lượng tồn kho
                int newQuantity = stock.getQuantity() + diffQty;
                if (newQuantity < 0) {
                    logger.warn("Tồn kho âm sau khi kiểm kê: productId={}, storeId={}, quantity={}, diff={}",
                            productId, storeId, stock.getQuantity(), diffQty);
                    newQuantity = 0; // Không cho phép tồn kho âm
                }

                stock.setQuantity(newQuantity);
                stockRepo.save(stock);

                logger.info("Đã cập nhật tồn kho: productId={}, storeId={}, diff={}, newQuantity={}",
                        productId, storeId, diffQty, newQuantity);
            }
        }
    }

    private InventoryCheckDto toDtoWithCalcTotal(InventoryCheck check) {
        List<InventoryCheckDetail> details = detailRepo.findByInventoryCheckId(check.getId());
        return toDtoWithCalcTotal(check, details, null);
    }

    private InventoryCheckDto toDtoWithCalcTotal(
            InventoryCheck check,
            List<InventoryCheckDetail> details,
            Map<Long, com.example.inventory_service.entity.ShopStore> storeMap) {
        BigDecimal totalDiff = BigDecimal.ZERO;
        List<InventoryCheckDetailDto> itemDtos = new ArrayList<>();

        if (details != null) {
            for (InventoryCheckDetail d : details) {
                if (d.getTotalValue() != null) {
                    totalDiff = totalDiff.add(d.getTotalValue());
                }

                InventoryCheckDetailDto itemDto = new InventoryCheckDetailDto();
                itemDto.setId(d.getId());
                itemDto.setProductId(d.getProductId());
                itemDto.setSystemQuantity(d.getSystemQuantity());
                itemDto.setActualQuantity(d.getActualQuantity());
                itemDto.setDifferenceQuantity(d.getDifferenceQuantity());
                itemDto.setUnitPrice(d.getUnitPrice());
                itemDto.setTotalValue(d.getTotalValue());
                itemDto.setNote(d.getNote());

                // TODO: Lấy thông tin sản phẩm từ product service
                itemDto.setProductCode(null);
                itemDto.setProductName(null);
                itemDto.setUnit(null);

                itemDtos.add(itemDto);
            }
        }

        InventoryCheckDto dto = toDto(check, totalDiff, storeMap);
        dto.setItems(itemDtos);
        return dto;
    }

    private InventoryCheckDto toDto(InventoryCheck check, BigDecimal totalDiff) {
        return toDto(check, totalDiff, null);
    }

    private InventoryCheckDto toDto(
            InventoryCheck check,
            BigDecimal totalDiff,
            Map<Long, com.example.inventory_service.entity.ShopStore> storeMap) {
        InventoryCheckDto dto = new InventoryCheckDto();
        dto.setId(check.getId());
        dto.setCheckCode(check.getCheckCode());
        dto.setStoreId(check.getStoreId());
        dto.setDescription(check.getDescription());
        dto.setStatus(check.getStatus());
        dto.setCheckDate(check.getCheckDate());
        dto.setCreatedBy(check.getCreatedBy());
        dto.setCreatedAt(check.getCreatedAt());
        dto.setApprovedBy(check.getApprovedBy());
        dto.setApprovedAt(check.getApprovedAt());
        dto.setConfirmedBy(check.getConfirmedBy());
        dto.setConfirmedAt(check.getConfirmedAt());
        dto.setRejectedBy(check.getRejectedBy());
        dto.setRejectedAt(check.getRejectedAt());
        dto.setNote(check.getNote());
        dto.setTotalDifferenceValue(totalDiff);

        // Lấy thông tin kho từ map nếu có, hoặc query từ DB
        if (check.getStoreId() != null) {
            if (storeMap != null && !storeMap.isEmpty()) {
                com.example.inventory_service.entity.ShopStore store = storeMap.get(check.getStoreId());
                if (store != null) {
                    dto.setStoreName(store.getName());
                }
            } else {
                storeRepo.findById(check.getStoreId()).ifPresent(store -> {
                    dto.setStoreName(store.getName());
                });
            }
        }

        // Map ảnh
        List<String> images = new ArrayList<>();
        String raw = check.getAttachmentImage();
        if (raw != null && !raw.isBlank()) {
            images = Arrays.stream(raw.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        dto.setAttachmentImages(images);

        // Lấy thông tin user names và roles
        try {
            if (check.getCreatedBy() != null) {
                String createdByUsername = getUserFullNameFromId(check.getCreatedBy());
                if (createdByUsername != null && !createdByUsername.trim().isEmpty()) {
                    dto.setCreatedByName(createdByUsername);
                }
                String createdByRole = getUserRoleFromId(check.getCreatedBy());
                if (createdByRole != null && !createdByRole.trim().isEmpty()) {
                    dto.setCreatedByRole(createdByRole);
                }
            }
            if (check.getApprovedBy() != null) {
                String approvedByUsername = getUserFullNameFromId(check.getApprovedBy());
                if (approvedByUsername != null && !approvedByUsername.trim().isEmpty()) {
                    dto.setApprovedByName(approvedByUsername);
                }
                String approvedByRole = getUserRoleFromId(check.getApprovedBy());
                if (approvedByRole != null && !approvedByRole.trim().isEmpty()) {
                    dto.setApprovedByRole(approvedByRole);
                }
            }
            if (check.getConfirmedBy() != null) {
                String confirmedByUsername = getUserFullNameFromId(check.getConfirmedBy());
                if (confirmedByUsername != null && !confirmedByUsername.trim().isEmpty()) {
                    dto.setConfirmedByName(confirmedByUsername);
                }
                String confirmedByRole = getUserRoleFromId(check.getConfirmedBy());
                if (confirmedByRole != null && !confirmedByRole.trim().isEmpty()) {
                    dto.setConfirmedByRole(confirmedByRole);
                }
            }
            if (check.getRejectedBy() != null) {
                String rejectedByUsername = getUserFullNameFromId(check.getRejectedBy());
                if (rejectedByUsername != null && !rejectedByUsername.trim().isEmpty()) {
                    dto.setRejectedByName(rejectedByUsername);
                }
                String rejectedByRole = getUserRoleFromId(check.getRejectedBy());
                if (rejectedByRole != null && !rejectedByRole.trim().isEmpty()) {
                    dto.setRejectedByRole(rejectedByRole);
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to get user names: " + e.getMessage());
            e.printStackTrace();
        }

        return dto;
    }

    private String getUserFullNameFromId(Long userId) {
        try {
            if (userRepo == null) {
                return null;
            }
            java.util.Optional<String> fullName = userRepo.findFullNameByUserId(userId);
            if (fullName.isPresent() && !fullName.get().trim().isEmpty()) {
                return fullName.get().trim();
            }
            java.util.Optional<String> username = userRepo.findUsernameByUserId(userId);
            return username.orElse(null);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to get user full name from userId " + userId + ": " + e.getMessage());
            return null;
        }
    }

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
     * Gửi activity log tới auth-service bằng Java 11 HttpClient.
     */
    private void sendActivityLog(Long userId, String username, String action, String resourceType, Long resourceId, String resourceName, String details) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("username", username);
            // try to include displayName if available
            try {
                String display = getUserFullNameFromId(userId);
                payload.put("displayName", display != null ? display : username);
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
            String endpoint = authUrl.endsWith("/") ? authUrl + "api/internal/activity-logs" : authUrl + "/api/internal/activity-logs";
            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(endpoint))
                    .header("Content-Type", "application/json");
            String token = System.getenv("ACTIVITY_LOG_SERVICE_TOKEN");
            if (token != null && !token.isBlank()) {
                builder.header("X-Activity-Log-Token", token);
            }
            java.net.http.HttpRequest req = builder.POST(java.net.http.HttpRequest.BodyPublishers.ofString(json)).build();
            System.out.println("Sending activity log to: " + endpoint + " payload: " + json);
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

    private Long getCurrentUserId() {
        try {
            if (userRepo == null) {
                System.err.println("⚠️ userRepo is null in getCurrentUserId");
                return null;
            }
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
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

    private String getCurrentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                return auth.getName();
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
