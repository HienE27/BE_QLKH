package com.example.product_service.repository;

import com.example.product_service.entity.ShopProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ShopProductRepository
        extends JpaRepository<ShopProduct, Long>, JpaSpecificationExecutor<ShopProduct> {

    boolean existsByUnitId(Long unitId);
    List<ShopProduct> findByCodeStartingWith(String prefix);
}