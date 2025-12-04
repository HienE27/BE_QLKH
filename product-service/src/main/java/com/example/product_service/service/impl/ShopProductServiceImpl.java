package com.example.product_service.service.impl;

import com.example.product_service.dto.ProductDto;
import com.example.product_service.dto.ProductRequest;
import com.example.product_service.entity.ShopProduct;
import com.example.product_service.exception.NotFoundException;
import com.example.product_service.repository.ShopCategoryRepository;
import com.example.product_service.repository.ShopProductRepository;
import com.example.product_service.service.ShopProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.ArrayList;

@Service
public class ShopProductServiceImpl implements ShopProductService {

    private final ShopProductRepository repo;
    private final ShopCategoryRepository categoryRepo;
    private final com.example.product_service.repository.ProductSupplierRepository productSupplierRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ✅ Constructor duy nhất, tiêm cả 2 repo
    public ShopProductServiceImpl(ShopProductRepository repo,
            ShopCategoryRepository categoryRepo,
            com.example.product_service.repository.ProductSupplierRepository productSupplierRepo) {
        this.repo = repo;
        this.categoryRepo = categoryRepo;
        this.productSupplierRepo = productSupplierRepo;
    }

    @Override
    public List<ProductDto> getAll() {
        return repo.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public ProductDto getById(Long id) {
        ShopProduct p = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
        return toDto(p);
    }

    @Override
    @Transactional
    public ProductDto create(ProductRequest request) {
        ShopProduct p = new ShopProduct();
        applyRequestToEntity(request, p);
        p.setCreatedAt(new Date());
        p.setUpdatedAt(new Date());
        ShopProduct saved = repo.save(p);

        // Lưu nhiều NCC vào junction table
        saveProductSuppliers(saved.getId(), request);

        return toDto(saved);
    }

    @Override
    @Transactional
    public ProductDto update(Long id, ProductRequest request) {
        ShopProduct p = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));

        applyRequestToEntity(request, p);
        p.setUpdatedAt(new Date());
        ShopProduct saved = repo.save(p);

        // Cập nhật nhiều NCC vào junction table
        saveProductSuppliers(saved.getId(), request);

        // Reload product để đảm bảo có dữ liệu mới nhất
        ShopProduct reloaded = repo.findById(saved.getId())
                .orElseThrow(() -> new NotFoundException("Product not found after update: " + id));

        return toDto(reloaded);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("Product not found: " + id);
        }

        try {
            repo.deleteById(id);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "Không thể xóa sản phẩm này vì đang được sử dụng trong phiếu nhập/xuất kho hoặc tồn kho. " +
                            "Vui lòng xóa các phiếu liên quan trước.");
        }
    }

    // ✅ search + phân trang
    @Override
    public Page<ProductDto> search(String code,
            String name,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable) {

        Specification<ShopProduct> spec = Specification.where(null);

        if (code != null && !code.isBlank()) {
            String likeCode = "%" + code.toLowerCase().trim() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("code")), likeCode));
        }

        if (name != null && !name.isBlank()) {
            String likeName = "%" + name.toLowerCase().trim() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), likeName));
        }

        if (fromDate != null) {
            Date from = Date.from(
                    fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }

        if (toDate != null) {
            Date to = Date.from(
                    toDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), to));
        }

        return repo.findAll(spec, pageable)
                .map(this::toDto);
    }

    // ---------- mapping helpers ----------

    private ProductDto toDto(ShopProduct p) {
        ProductDto dto = new ProductDto();
        dto.setId(p.getId());
        dto.setCode(p.getCode());
        dto.setName(p.getName());
        dto.setShortDescription(p.getShortDescription());
        dto.setImage(p.getImage());
        dto.setUnitPrice(p.getUnitPrice());
        dto.setStatus(p.getStatus());
        dto.setCategoryId(p.getCategoryId());
        dto.setSupplierId(p.getSupplierId());
        dto.setUnitId(p.getUnitId());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());

        // ⭐ LẤY CATEGORY NAME
        if (p.getCategoryId() != null) {
            categoryRepo.findById(p.getCategoryId())
                    .ifPresent(cat -> dto.setCategoryName(cat.getName()));
        }

        // ⭐ LẤY DANH SÁCH NCC TỪ JUNCTION TABLE
        try {
            List<com.example.product_service.entity.ProductSupplier> productSuppliers = productSupplierRepo
                    .findByProductId(p.getId());
            if (productSuppliers != null && !productSuppliers.isEmpty()) {
                List<Long> supplierIds = productSuppliers.stream()
                        .map(com.example.product_service.entity.ProductSupplier::getSupplierId)
                        .filter(id -> id != null)
                        .distinct()
                        .collect(Collectors.toList());

                if (!supplierIds.isEmpty()) {
                    dto.setSupplierIds(supplierIds);

                    // Set NCC chính (isPrimary = true) hoặc NCC đầu tiên
                    Optional<com.example.product_service.entity.ProductSupplier> primary = productSuppliers.stream()
                            .filter(ps -> Boolean.TRUE.equals(ps.getIsPrimary()))
                            .findFirst();

                    if (primary.isPresent()) {
                        dto.setSupplierId(primary.get().getSupplierId());
                    } else if (!supplierIds.isEmpty()) {
                        dto.setSupplierId(supplierIds.get(0));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(
                    "Error loading product suppliers for productId: " + p.getId() + ", error: " + e.getMessage());
            e.printStackTrace();
            // Nếu lỗi, fallback về supplierId từ entity
        }

        // Nếu không có trong junction table, dùng supplierId từ entity (tương thích
        // ngược)
        if (dto.getSupplierIds() == null || dto.getSupplierIds().isEmpty()) {
            if (p.getSupplierId() != null) {
                dto.setSupplierIds(Arrays.asList(p.getSupplierId()));
                dto.setSupplierId(p.getSupplierId());
            }
        }

        // Xử lý supplierIds từ JSON nếu có
        if (p.getSupplierIds() != null && !p.getSupplierIds().trim().isEmpty()) {
            try {
                List<Long> idsFromJson = objectMapper.readValue(
                        p.getSupplierIds(),
                        new TypeReference<List<Long>>() {
                        });
                if (!idsFromJson.isEmpty()) {
                    dto.setSupplierIds(idsFromJson);
                    if (dto.getSupplierId() == null && !idsFromJson.isEmpty()) {
                        dto.setSupplierId(idsFromJson.get(0));
                    }
                }
            } catch (Exception e) {
                // Nếu parse JSON lỗi, bỏ qua
            }
        }

        return dto;
    }

    private void applyRequestToEntity(ProductRequest req, ShopProduct p) {
        // Tự động tạo mã nếu không có trong request
        if (req.getCode() != null && !req.getCode().isBlank()) {
            p.setCode(req.getCode());
        } else if (p.getCode() == null || p.getCode().isBlank()) {
            p.setCode(generateProductCode());
        }
        p.setName(req.getName());
        // Cắt ngắn mô tả nếu quá dài (giới hạn 5000 ký tự để an toàn)
        String description = req.getShortDescription();
        if (description != null && description.length() > 5000) {
            description = description.substring(0, 5000);
        }
        p.setShortDescription(description);
        p.setImage(req.getImage());
        p.setUnitPrice(req.getUnitPrice());
        p.setStatus(req.getStatus());

        // Chỉ cập nhật categoryId nếu có giá trị mới, giữ nguyên nếu null
        // Đảm bảo categoryId luôn có giá trị (không null) vì database constraint
        if (req.getCategoryId() != null) {
            p.setCategoryId(req.getCategoryId());
        } else if (p.getCategoryId() == null) {
            // Nếu cả request và entity đều null, throw exception
            throw new IllegalArgumentException("categoryId không được để trống");
        }

        // Xử lý supplierIds: ưu tiên supplierIds, nếu không có thì dùng supplierId
        List<Long> supplierIdsList = req.getSupplierIds();
        if (supplierIdsList != null && !supplierIdsList.isEmpty()) {
            // Lưu danh sách NCC vào JSON field
            try {
                p.setSupplierIds(objectMapper.writeValueAsString(supplierIdsList));
            } catch (Exception e) {
                // Nếu lỗi, bỏ qua
            }
            // Lấy NCC đầu tiên làm NCC chính (tương thích ngược)
            p.setSupplierId(supplierIdsList.get(0));
        } else if (req.getSupplierId() != null) {
            p.setSupplierId(req.getSupplierId());
            // Lưu vào JSON nếu chỉ có 1 NCC
            try {
                p.setSupplierIds(objectMapper.writeValueAsString(Arrays.asList(req.getSupplierId())));
            } catch (Exception e) {
                // Nếu lỗi, bỏ qua
            }
        }

        // Chỉ cập nhật unitId nếu có giá trị mới, giữ nguyên nếu null
        if (req.getUnitId() != null) {
            p.setUnitId(req.getUnitId());
        }
    }

    // Lưu nhiều NCC vào junction table
    private void saveProductSuppliers(Long productId, ProductRequest request) {
        try {
            List<Long> supplierIdsList = request.getSupplierIds();

            // Nếu không có supplierIds trong request, giữ nguyên NCC cũ (không cập nhật)
            if (supplierIdsList == null || supplierIdsList.isEmpty()) {
                // Nếu không có supplierIds, kiểm tra supplierId (tương thích ngược)
                if (request.getSupplierId() != null) {
                    supplierIdsList = Arrays.asList(request.getSupplierId());
                } else {
                    // Nếu cả supplierIds và supplierId đều null, giữ nguyên NCC cũ
                    return;
                }
            }

            // Validate supplier IDs không null và không trùng
            supplierIdsList = supplierIdsList.stream()
                    .filter(id -> id != null)
                    .distinct()
                    .collect(Collectors.toList());

            if (supplierIdsList.isEmpty()) {
                // Nếu không có NCC hợp lệ, xóa hết NCC cũ
                productSupplierRepo.deleteByProductId(productId);
                return;
            }

            // Lấy danh sách NCC hiện tại
            List<com.example.product_service.entity.ProductSupplier> existingSuppliers = productSupplierRepo
                    .findByProductId(productId);

            // Tạo map để kiểm tra nhanh
            Map<Long, com.example.product_service.entity.ProductSupplier> existingMap = existingSuppliers.stream()
                    .collect(Collectors.toMap(
                            com.example.product_service.entity.ProductSupplier::getSupplierId,
                            ps -> ps,
                            (existing, replacement) -> existing));

            // Xóa các NCC không còn trong danh sách mới
            List<Long> supplierIdsToKeep = new ArrayList<>(supplierIdsList);
            for (com.example.product_service.entity.ProductSupplier existing : existingSuppliers) {
                if (!supplierIdsToKeep.contains(existing.getSupplierId())) {
                    productSupplierRepo.delete(existing);
                }
            }

            // Lưu hoặc cập nhật từng NCC
            for (int i = 0; i < supplierIdsList.size(); i++) {
                Long supplierId = supplierIdsList.get(i);
                if (supplierId == null) {
                    continue; // Bỏ qua nếu null
                }

                com.example.product_service.entity.ProductSupplier ps = existingMap.get(supplierId);
                boolean isNew = ps == null;

                if (isNew) {
                    ps = new com.example.product_service.entity.ProductSupplier();
                    ps.setProductId(productId);
                    ps.setSupplierId(supplierId);
                }

                // Cập nhật isPrimary (NCC đầu tiên là NCC chính)
                ps.setIsPrimary(i == 0);

                productSupplierRepo.save(ps);
            }
        } catch (Exception e) {
            System.err.println(
                    "Error in saveProductSuppliers for productId: " + productId + ", error: " + e.getMessage());
            e.printStackTrace();
            throw new IllegalStateException("Không thể lưu danh sách NCC: " + e.getMessage(), e);
        }
    }

    /**
     * Tự động tạo mã sản phẩm: SP + 5 số (ví dụ: SP00001)
     */
    private String generateProductCode() {
        String prefix = "SP";
        List<ShopProduct> existing = repo.findByCodeStartingWith(prefix);
        long maxNumber = 0;

        for (ShopProduct p : existing) {
            if (p.getCode() != null && p.getCode().length() >= 3) {
                try {
                    String numberPart = p.getCode().substring(2);
                    long num = Long.parseLong(numberPart);
                    if (num > maxNumber) {
                        maxNumber = num;
                    }
                } catch (NumberFormatException e) {
                    // Bỏ qua nếu không parse được số
                }
            }
        }

        long nextNumber = maxNumber + 1;
        return prefix + String.format("%05d", nextNumber);
    }

}
