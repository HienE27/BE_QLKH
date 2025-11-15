package com.example.product_service.service;

import com.example.product_service.entity.ShopProductImage;

import java.util.List;

public interface ShopProductImageService {

    List<ShopProductImage> findByProduct(Long productId);
}
