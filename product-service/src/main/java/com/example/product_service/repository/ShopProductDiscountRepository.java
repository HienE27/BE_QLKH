package com.example.product_service.repository;

import com.example.product_service.entity.ShopProductDiscount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopProductDiscountRepository extends JpaRepository<ShopProductDiscount, Long> {
    List<ShopProductDiscount> findByProductId(Long productId);
}
