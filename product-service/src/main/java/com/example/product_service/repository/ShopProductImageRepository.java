package com.example.product_service.repository;

import com.example.product_service.entity.ShopProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopProductImageRepository extends JpaRepository<ShopProductImage, Long> {
    List<ShopProductImage> findByProductId(Long productId);
}
