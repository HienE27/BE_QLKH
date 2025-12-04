package com.example.product_service.service.impl;

import com.example.product_service.entity.ShopProductDiscount;
import com.example.product_service.repository.ShopProductDiscountRepository;
import com.example.product_service.service.ShopProductDiscountService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShopProductDiscountServiceImpl implements ShopProductDiscountService {

    private final ShopProductDiscountRepository repo;

    public ShopProductDiscountServiceImpl(ShopProductDiscountRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<ShopProductDiscount> findByProduct(Long productId) {
        return repo.findByProductId(productId);
    }
}
