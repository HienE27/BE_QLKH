package com.example.inventory_service.repository;

import com.example.inventory_service.entity.ShopImportDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopImportDetailRepository extends JpaRepository<ShopImportDetail, Long> {

    List<ShopImportDetail> findByImportId(Long importId);

    // dùng cho tính tồn kho
    List<ShopImportDetail> findByProductId(Long productId);

}
