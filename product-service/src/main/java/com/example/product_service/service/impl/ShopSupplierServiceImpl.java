package com.example.product_service.service.impl;

import com.example.product_service.entity.ShopSupplier;
import com.example.product_service.exception.NotFoundException;
import com.example.product_service.repository.ShopSupplierRepository;
import com.example.product_service.service.ShopSupplierService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShopSupplierServiceImpl implements ShopSupplierService {

    private final ShopSupplierRepository repo;

    public ShopSupplierServiceImpl(ShopSupplierRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<ShopSupplier> findAll() {
        return repo.findAll();
    }

    @Override
    public List<ShopSupplier> findByType(String type) {
        return repo.findByType(type);
    }

    @Override
    public Page<ShopSupplier> search(String code, String name, String type, String phone, Pageable pageable) {
        return repo.search(code, name, type, phone, pageable);
    }

    @Override
    public ShopSupplier getById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Supplier not found with id = " + id));
    }

    @Override
    public ShopSupplier create(ShopSupplier supplier) {
        // Tự động tạo mã nếu không có
        if (supplier.getCode() == null || supplier.getCode().isBlank()) {
            supplier.setCode(generateSupplierCode());
        }
        // Set timestamps nếu chưa có
        if (supplier.getCreatedAt() == null) {
            supplier.setCreatedAt(LocalDateTime.now());
        }
        if (supplier.getUpdatedAt() == null) {
            supplier.setUpdatedAt(LocalDateTime.now());
        }
        return repo.save(supplier);
    }

    /**
     * Tự động tạo mã nhà cung cấp: NH + 5 số (ví dụ: NH00001)
     */
    private String generateSupplierCode() {
        String prefix = "NH";
        List<ShopSupplier> existing = repo.findByCodeStartingWith(prefix);
        long maxNumber = 0;

        for (ShopSupplier s : existing) {
            if (s.getCode() != null && s.getCode().length() >= 3) {
                try {
                    String numberPart = s.getCode().substring(2);
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

    @Override
    public ShopSupplier update(Long id, ShopSupplier supplier) {
        ShopSupplier db = getById(id);
        db.setCode(supplier.getCode());
        db.setName(supplier.getName());
        db.setType(supplier.getType());
        db.setAddress(supplier.getAddress());
        db.setPhone(supplier.getPhone());
        db.setEmail(supplier.getEmail());
        db.setDescription(supplier.getDescription());
        db.setImage(supplier.getImage());
        db.setCreatedAt(supplier.getCreatedAt());
        db.setUpdatedAt(supplier.getUpdatedAt());
        return repo.save(db);
    }

    @Override
    public void delete(Long id) {
        ShopSupplier db = getById(id);
        repo.delete(db);
    }
}
