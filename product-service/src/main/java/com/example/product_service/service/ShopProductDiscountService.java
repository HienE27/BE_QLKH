package com.example.product_service.service;

import com.example.product_service.entity.ShopProductDiscount;

import java.util.List;

public interface ShopProductDiscountService {

    List<ShopProductDiscount> findByProduct(Long productId);
}
