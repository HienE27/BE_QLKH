package com.example.inventory_service.repository;

import com.example.inventory_service.entity.ShopImportDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ShopImportDetailRepository extends JpaRepository<ShopImportDetail, Long> {

    List<ShopImportDetail> findByImportId(Long importId);

    // Batch fetch để tránh N+1 query problem
    List<ShopImportDetail> findByImportIdIn(List<Long> importIds);

    // Calculate totals for multiple imports in a single query
    @Query("""
            SELECT d.importId,
                   SUM(d.unitPrice * d.quantity * (1 - COALESCE(d.discountPercent, 0) / 100))
            FROM ShopImportDetail d
            WHERE d.importId IN :importIds
            GROUP BY d.importId
            """)
    List<Object[]> sumTotalsByImportIds(@Param("importIds") List<Long> importIds);

    // dùng cho tính tồn kho
    List<ShopImportDetail> findByProductId(Long productId);

    // xóa chi tiết theo importId (dùng cho update)
    @Modifying
    @Transactional
    void deleteByImportId(Long importId);

    // Lấy chi tiết của các phiếu đã IMPORTED (để tính tồn kho chính xác)
    @Query("SELECT d FROM ShopImportDetail d JOIN ShopImport i ON d.importId = i.id WHERE i.status = 'IMPORTED'")
    List<ShopImportDetail> findAllImported();

    // Lấy chi tiết theo productId và status = IMPORTED
    @Query("SELECT d FROM ShopImportDetail d JOIN ShopImport i ON d.importId = i.id WHERE d.productId = :productId AND i.status = 'IMPORTED'")
    List<ShopImportDetail> findImportedByProductId(@Param("productId") Long productId);

}
