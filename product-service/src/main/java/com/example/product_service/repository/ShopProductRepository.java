package com.example.product_service.repository;

import com.example.product_service.entity.ShopProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ShopProductRepository
        extends JpaRepository<ShopProduct, Long>, JpaSpecificationExecutor<ShopProduct> {
}
