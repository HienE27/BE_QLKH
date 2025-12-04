package com.example.inventory_service.repository;

import com.example.inventory_service.entity.ShopExport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Date;
import java.util.List;

public interface ShopExportRepository extends JpaRepository<ShopExport, Long> {

  List<ShopExport> findByStoreId(Long storeId);

  List<ShopExport> findByOrderId(Long orderId);

  @Query("""
         SELECT e FROM ShopExport e
         WHERE e.exportType = 'SUPPLIER'
           AND (:status IS NULL OR e.status = :status)
           AND (:code IS NULL OR e.code LIKE CONCAT('%', :code, '%'))
           AND (:fromDate IS NULL OR e.exportsDate >= :fromDate)
           AND (:toDate IS NULL OR e.exportsDate < :toDate)
         ORDER BY e.exportsDate DESC
      """)
  List<ShopExport> searchSupplierExports(
      @Param("status") String status,
      @Param("code") String code,
      @Param("fromDate") Date fromDate,
      @Param("toDate") Date toDate);

  @Query("""
         SELECT e FROM ShopExport e
         WHERE e.exportType = 'INTERNAL'
           AND (:status IS NULL OR e.status = :status)
           AND (:code IS NULL OR e.code LIKE CONCAT('%', :code, '%'))
           AND (:fromDate IS NULL OR e.exportsDate >= :fromDate)
           AND (:toDate IS NULL OR e.exportsDate < :toDate)
         ORDER BY e.exportsDate DESC
      """)
  List<ShopExport> searchInternalExports(
      @Param("status") String status,
      @Param("code") String code,
      @Param("fromDate") Date fromDate,
      @Param("toDate") Date toDate);

  @Query("""
         SELECT e FROM ShopExport e
         WHERE e.exportType = 'STAFF'
           AND (:status IS NULL OR e.status = :status)
           AND (:code IS NULL OR e.code LIKE CONCAT('%', :code, '%'))
           AND (:fromDate IS NULL OR e.exportsDate >= :fromDate)
           AND (:toDate IS NULL OR e.exportsDate < :toDate)
         ORDER BY e.exportsDate DESC
      """)
  List<ShopExport> searchStaffExports(
      @Param("status") String status,
      @Param("code") String code,
      @Param("fromDate") Date fromDate,
      @Param("toDate") Date toDate);

  // Unified search - không filter theo type
  @Query("""
         SELECT e FROM ShopExport e
         WHERE (:status IS NULL OR e.status = :status)
           AND (:code IS NULL OR e.code LIKE CONCAT('%', :code, '%'))
           AND (:fromDate IS NULL OR e.exportsDate >= :fromDate)
           AND (:toDate IS NULL OR e.exportsDate < :toDate)
         ORDER BY e.exportsDate DESC
      """)
  List<ShopExport> searchAllExports(
      @Param("status") String status,
      @Param("code") String code,
      @Param("fromDate") Date fromDate,
      @Param("toDate") Date toDate);

  // Unified search với pagination
  @Query("""
         SELECT e FROM ShopExport e
         WHERE (:status IS NULL OR e.status = :status)
           AND (:code IS NULL OR e.code LIKE CONCAT('%', :code, '%'))
           AND (:fromDate IS NULL OR e.exportsDate >= :fromDate)
           AND (:toDate IS NULL OR e.exportsDate < :toDate)
           AND (:storeId IS NULL OR e.storeId = :storeId)
         ORDER BY e.exportsDate DESC
      """)
  Page<ShopExport> searchAllExports(
      @Param("status") String status,
      @Param("code") String code,
      @Param("fromDate") Date fromDate,
      @Param("toDate") Date toDate,
      @Param("storeId") Long storeId,
      Pageable pageable);
}
