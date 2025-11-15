package com.example.inventory_service.repository;

import com.example.inventory_service.entity.ShopStore;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopStoreRepository extends JpaRepository<ShopStore, Long> {
    
}
