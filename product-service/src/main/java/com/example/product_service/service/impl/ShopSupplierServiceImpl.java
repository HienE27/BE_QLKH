package com.example.product_service.service.impl;

import com.example.product_service.entity.ShopSupplier;
import com.example.product_service.exception.NotFoundException;
import com.example.product_service.repository.ShopSupplierRepository;
import com.example.product_service.service.ShopSupplierService;
import org.springframework.stereotype.Service;

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
    public ShopSupplier getById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Supplier not found with id = " + id));
    }

    @Override
    public ShopSupplier create(ShopSupplier supplier) {
        return repo.save(supplier);
    }

    @Override
    public ShopSupplier update(Long id, ShopSupplier supplier) {
        ShopSupplier db = getById(id);
        db.setCode(supplier.getCode());
        db.setName(supplier.getName());
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
