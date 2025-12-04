package com.example.inventory_service.repository;

import com.example.inventory_service.entity.InventoryCheckDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryCheckDetailRepository extends JpaRepository<InventoryCheckDetail, Long> {

    List<InventoryCheckDetail> findByInventoryCheckId(Long inventoryCheckId);

    void deleteByInventoryCheckId(Long inventoryCheckId);
}
