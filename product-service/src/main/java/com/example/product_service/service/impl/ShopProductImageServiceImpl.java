package com.example.product_service.service.impl;

import com.example.product_service.entity.ShopProductImage;
import com.example.product_service.repository.ShopProductImageRepository;
import com.example.product_service.service.ShopProductImageService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShopProductImageServiceImpl implements ShopProductImageService {

    private final ShopProductImageRepository repo;

    public ShopProductImageServiceImpl(ShopProductImageRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<ShopProductImage> findByProduct(Long productId) {
        return repo.findByProductId(productId);
    }
}
