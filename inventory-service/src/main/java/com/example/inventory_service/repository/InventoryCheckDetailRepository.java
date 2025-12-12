package com.example.inventory_service.repository;

import com.example.inventory_service.entity.InventoryCheckDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryCheckDetailRepository extends JpaRepository<InventoryCheckDetail, Long> {

    List<InventoryCheckDetail> findByInventoryCheckId(Long inventoryCheckId);

    // Batch fetch để tránh N+1 query problem
    List<InventoryCheckDetail> findByInventoryCheckIdIn(List<Long> inventoryCheckIds);

    void deleteByInventoryCheckId(Long inventoryCheckId);
}
