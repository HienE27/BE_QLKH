package com.example.product_service.service;

import com.example.product_service.entity.ShopSupplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ShopSupplierService {

    List<ShopSupplier> findAll();

    List<ShopSupplier> findByType(String type);

    Page<ShopSupplier> search(String code, String name, String type, String phone, Pageable pageable);

    ShopSupplier getById(Long id);

    ShopSupplier create(ShopSupplier supplier);

    ShopSupplier update(Long id, ShopSupplier supplier);

    void delete(Long id);
}
