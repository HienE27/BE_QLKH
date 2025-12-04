package com.example.product_service.repository;

import com.example.product_service.entity.ShopSupplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShopSupplierRepository extends JpaRepository<ShopSupplier, Long> {
    List<ShopSupplier> findByType(String type);
    List<ShopSupplier> findByCodeStartingWith(String prefix);
    
    // Search với pagination
    @Query("""
        SELECT s FROM ShopSupplier s
        WHERE (:code IS NULL OR s.code LIKE CONCAT('%', :code, '%'))
          AND (:name IS NULL OR s.name LIKE CONCAT('%', :name, '%'))
          AND (:type IS NULL OR s.type = :type)
          AND (:phone IS NULL OR s.phone LIKE CONCAT('%', :phone, '%'))
        ORDER BY s.name ASC
        """)
    Page<ShopSupplier> search(
        @Param("code") String code,
        @Param("name") String name,
        @Param("type") String type,
        @Param("phone") String phone,
        Pageable pageable);
}
