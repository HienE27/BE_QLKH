package com.example.product_service.service;

import com.example.product_service.entity.ShopSupplier;

import java.util.List;

public interface ShopSupplierService {

    List<ShopSupplier> findAll();

    ShopSupplier getById(Long id);

    ShopSupplier create(ShopSupplier supplier);

    ShopSupplier update(Long id, ShopSupplier supplier);

    void delete(Long id);
}
