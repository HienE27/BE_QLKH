package com.example.inventory_service.repository;

import com.example.inventory_service.entity.ShopExportDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ShopExportDetailRepository extends JpaRepository<ShopExportDetail, Long> {

    List<ShopExportDetail> findByExportId(Long exportId);

    // dùng cho tính tồn kho
    List<ShopExportDetail> findByProductId(Long productId);

    // xóa chi tiết theo exportId (dùng cho update)
    @Modifying
    @Transactional
    void deleteByExportId(Long exportId);

    // Lấy chi tiết của các phiếu đã EXPORTED (để tính tồn kho chính xác)
    @Query("SELECT d FROM ShopExportDetail d JOIN ShopExport e ON d.exportId = e.id WHERE e.status = 'EXPORTED'")
    List<ShopExportDetail> findAllExported();

    // Lấy chi tiết theo productId và status = EXPORTED
    @Query("SELECT d FROM ShopExportDetail d JOIN ShopExport e ON d.exportId = e.id WHERE d.productId = :productId AND e.status = 'EXPORTED'")
    List<ShopExportDetail> findExportedByProductId(@Param("productId") Long productId);
}
