package com.example.inventory_service.repository;

import com.example.inventory_service.entity.ShopExport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopExportRepository extends JpaRepository<ShopExport, Long> {

    List<ShopExport> findByStoreId(Long storeId);

    List<ShopExport> findByOrderId(Long orderId);
}
