package com.example.inventory_service.repository;

import com.example.inventory_service.entity.ExportStatus;
import com.example.inventory_service.entity.ExportType;
import com.example.inventory_service.entity.ShopExport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ShopExportRepository extends JpaRepository<ShopExport, Long> {

  Page<ShopExport> findByStoreId(Long storeId, Pageable pageable);

  Page<ShopExport> findByOrderId(Long orderId, Pageable pageable);

  // Unified search with pagination
  @Query("""
         SELECT e FROM ShopExport e
         WHERE (:status IS NULL OR e.status = :status)
           AND (:code IS NULL OR e.code LIKE CONCAT('%', :code, '%'))
           AND (:fromDate IS NULL OR e.exportsDate >= :fromDate)
           AND (:toDate IS NULL OR e.exportsDate < :toDate)
         ORDER BY e.exportsDate DESC, e.id DESC
      """)
  Page<ShopExport> searchAllExportsPaged(
      @Param("status") ExportStatus status,
      @Param("code") String code,
      @Param("fromDate") LocalDateTime fromDate,
      @Param("toDate") LocalDateTime toDate,
      Pageable pageable);

  // Keyset pagination - faster for large datasets
  @Query("""
         SELECT e FROM ShopExport e
         WHERE (:status IS NULL OR e.status = :status)
           AND (:code IS NULL OR e.code LIKE CONCAT('%', :code, '%'))
           AND (:fromDate IS NULL OR e.exportsDate >= :fromDate)
           AND (:toDate IS NULL OR e.exportsDate < :toDate)
           AND (:lastDate IS NULL OR :lastId IS NULL OR 
                (e.exportsDate < :lastDate OR (e.exportsDate = :lastDate AND e.id < :lastId)))
         ORDER BY e.exportsDate DESC, e.id DESC
      """)
  Page<ShopExport> searchAllExportsKeyset(
      @Param("status") ExportStatus status,
      @Param("code") String code,
      @Param("fromDate") LocalDateTime fromDate,
      @Param("toDate") LocalDateTime toDate,
      @Param("lastDate") LocalDateTime lastDate,
      @Param("lastId") Long lastId,
      Pageable pageable);

  // Projection query for total values - optimized
  @Query("""
         SELECT e.id, e.code, e.status, e.exportsDate, e.storeId, e.customerId,
                e.customerName, e.customerPhone, e.customerAddress,
                COALESCE(SUM(d.unitPrice * d.quantity * (1 - COALESCE(d.discountPercent, 0) / 100)), 0) as totalValue
         FROM ShopExport e
         LEFT JOIN ShopExportDetail d ON d.exportId = e.id
         WHERE (:status IS NULL OR e.status = :status)
           AND (:code IS NULL OR e.code LIKE CONCAT('%', :code, '%'))
           AND (:fromDate IS NULL OR e.exportsDate >= :fromDate)
           AND (:toDate IS NULL OR e.exportsDate < :toDate)
         GROUP BY e.id, e.code, e.status, e.exportsDate, e.storeId, e.customerId,
                  e.customerName, e.customerPhone, e.customerAddress
         ORDER BY e.exportsDate DESC, e.id DESC
      """)
  Page<Object[]> searchAllExportsWithTotalPaged(
      @Param("status") ExportStatus status,
      @Param("code") String code,
      @Param("fromDate") LocalDateTime fromDate,
      @Param("toDate") LocalDateTime toDate,
      Pageable pageable);
}
