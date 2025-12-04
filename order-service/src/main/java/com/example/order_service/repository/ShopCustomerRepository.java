package com.example.order_service.repository;

import com.example.order_service.entity.ShopCustomer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShopCustomerRepository extends JpaRepository<ShopCustomer, Long> {
    List<ShopCustomer> findByCodeStartingWith(String prefix);
    
    // Search với pagination
    @Query("""
        SELECT c FROM ShopCustomer c
        WHERE (:code IS NULL OR c.code LIKE CONCAT('%', :code, '%'))
          AND (:name IS NULL OR c.name LIKE CONCAT('%', :name, '%'))
          AND (:phone IS NULL OR c.phone LIKE CONCAT('%', :phone, '%'))
        ORDER BY c.name ASC
        """)
    Page<ShopCustomer> search(
        @Param("code") String code,
        @Param("name") String name,
        @Param("phone") String phone,
        Pageable pageable);
}
