package com.example.inventory_service.repository;

import com.example.inventory_service.entity.ShopImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopImportRepository extends JpaRepository<ShopImport, Long> {

    List<ShopImport> findByStoreId(Long storeId);

    
}
