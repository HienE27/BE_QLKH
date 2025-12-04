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
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InventoryCheckServiceImpl implements InventoryCheckService {

    private final InventoryCheckRepository checkRepo;
    private final InventoryCheckDetailRepository detailRepo;
    private final ProductServiceClient productClient;
    private final com.example.inventory_service.repository.ShopStoreRepository storeRepo;

    public InventoryCheckServiceImpl(
            InventoryCheckRepository checkRepo,
            InventoryCheckDetailRepository detailRepo,
            ProductServiceClient productClient,
            com.example.inventory_service.repository.ShopStoreRepository storeRepo) {
        this.checkRepo = checkRepo;
        this.detailRepo = detailRepo;
        this.productClient = productClient;
        this.storeRepo = storeRepo;
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
        check.setCheckDate(request.getCheckDate() != null ? request.getCheckDate() : now);
        check.setNote(request.getNote());
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

        List<InventoryCheck> list = checkRepo.searchInventoryChecks(
                status,
                checkCode,
                fromDate,
                toDate);

        List<InventoryCheckDto> result = new ArrayList<>();
        for (InventoryCheck check : list) {
            result.add(toDtoWithCalcTotal(check));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InventoryCheckDto> search(
            String status,
            String checkCode,
            LocalDate from,
            LocalDate to,
            Long storeId,
            Pageable pageable) {
        Date fromDate = from != null ? java.sql.Date.valueOf(from) : null;
        Date toDate = to != null ? java.sql.Date.valueOf(to.plusDays(1)) : null;

        Page<InventoryCheck> page = checkRepo.searchInventoryChecks(
                status,
                checkCode,
                fromDate,
                toDate,
                storeId,
                pageable);

        List<InventoryCheckDto> content = page.getContent().stream()
                .map(this::toDtoWithCalcTotal)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, page.getTotalElements());
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
        check.setApprovedAt(new Date());
        check.setUpdatedAt(new Date());
        check = checkRepo.save(check);

        // Cập nhật tồn kho theo chênh lệch
        List<InventoryCheckDetail> details = detailRepo.findByInventoryCheckId(id);
        for (InventoryCheckDetail d : details) {
            if (d.getDifferenceQuantity() != null && d.getDifferenceQuantity() != 0) {
                if (d.getDifferenceQuantity() > 0) {
                    // Thực tế nhiều hơn hệ thống → tăng tồn
                    productClient.increaseQuantity(d.getProductId(), d.getDifferenceQuantity());
                } else {
                    // Thực tế ít hơn hệ thống → giảm tồn
                    productClient.decreaseQuantity(d.getProductId(), Math.abs(d.getDifferenceQuantity()));
                }
            }
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
        check.setUpdatedAt(new Date());
        check = checkRepo.save(check);

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

    private InventoryCheckDto toDtoWithCalcTotal(InventoryCheck check) {
        List<InventoryCheckDetail> details = detailRepo.findByInventoryCheckId(check.getId());
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

        InventoryCheckDto dto = toDto(check, totalDiff);
        dto.setItems(itemDtos);
        return dto;
    }

    private InventoryCheckDto toDto(InventoryCheck check, BigDecimal totalDiff) {
        InventoryCheckDto dto = new InventoryCheckDto();
        dto.setId(check.getId());
        dto.setCheckCode(check.getCheckCode());
        dto.setStoreId(check.getStoreId());
        dto.setDescription(check.getDescription());
        dto.setStatus(check.getStatus());
        dto.setCheckDate(check.getCheckDate());
        dto.setCreatedBy(check.getCreatedBy());
        dto.setApprovedBy(check.getApprovedBy());
        dto.setApprovedAt(check.getApprovedAt());
        dto.setNote(check.getNote());
        dto.setTotalDifferenceValue(totalDiff);

        // Lấy thông tin kho
        if (check.getStoreId() != null) {
            storeRepo.findById(check.getStoreId()).ifPresent(store -> {
                dto.setStoreName(store.getName());
            });
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

        return dto;
    }
}
