package com.example.inventory_service.repository;

import com.example.inventory_service.entity.ShopStore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopStoreRepository extends JpaRepository<ShopStore, Long> {
    List<ShopStore> findByCodeStartingWith(String prefix);
}
