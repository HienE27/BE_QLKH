package com.example.product_service.repository;

import com.example.product_service.entity.ProductSupplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductSupplierRepository extends JpaRepository<ProductSupplier, Long> {
    
    List<ProductSupplier> findByProductId(Long productId);
    
    List<ProductSupplier> findBySupplierId(Long supplierId);
    
    Optional<ProductSupplier> findByProductIdAndSupplierId(Long productId, Long supplierId);
    
    void deleteByProductId(Long productId);
    
    void deleteByProductIdAndSupplierId(Long productId, Long supplierId);
    
    List<ProductSupplier> findByProductIdAndIsPrimaryTrue(Long productId);
}

