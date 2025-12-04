package com.example.product_service.repository;

import com.example.product_service.entity.ShopProductUnit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopProductUnitRepository extends JpaRepository<ShopProductUnit, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}


