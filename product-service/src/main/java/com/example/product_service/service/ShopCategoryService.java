package com.example.product_service.service;

import com.example.product_service.entity.ShopCategory;

import java.util.List;

public interface ShopCategoryService {

    List<ShopCategory> findAll();

    ShopCategory getById(Long id);

    ShopCategory create(ShopCategory category);

    ShopCategory update(Long id, ShopCategory category);

    void delete(Long id);
}
