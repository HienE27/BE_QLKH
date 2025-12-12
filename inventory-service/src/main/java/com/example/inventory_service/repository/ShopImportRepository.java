package com.example.inventory_service.repository;

import com.example.inventory_service.entity.ImportStatus;
import com.example.inventory_service.entity.ShopImport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ShopImportRepository extends JpaRepository<ShopImport, Long> {

  List<ShopImport> findByStoreId(Long storeId);

  // Pagination version để tránh load quá nhiều records
  Page<ShopImport> findByStoreId(Long storeId, Pageable pageable);

  // Unified search with pagination - using enum and LocalDateTime
  @Query("""
      SELECT i FROM ShopImport i
      WHERE (:status IS NULL OR i.status = :status)
        AND (:code IS NULL OR i.code LIKE CONCAT('%', :code, '%'))
        AND (:fromDate IS NULL OR i.importsDate >= :fromDate)
        AND (:toDate IS NULL OR i.importsDate < :toDate)
      ORDER BY i.importsDate DESC, i.id DESC
      """)
  Page<ShopImport> searchAllImportsPaged(
      @Param("status") ImportStatus status,
      @Param("code") String code,
      @Param("fromDate") LocalDateTime fromDate,
      @Param("toDate") LocalDateTime toDate,
      Pageable pageable);

  // Keyset pagination for better performance with large datasets
  @Query("""
      SELECT i FROM ShopImport i
      WHERE (:status IS NULL OR i.status = :status)
        AND (:code IS NULL OR i.code LIKE CONCAT('%', :code, '%'))
        AND (:fromDate IS NULL OR i.importsDate >= :fromDate)
        AND (:toDate IS NULL OR i.importsDate < :toDate)
        AND (:lastDate IS NULL OR i.importsDate < :lastDate OR (i.importsDate = :lastDate AND i.id < :lastId))
      ORDER BY i.importsDate DESC, i.id DESC
      """)
  Page<ShopImport> searchAllImportsKeyset(
      @Param("status") ImportStatus status,
      @Param("code") String code,
      @Param("fromDate") LocalDateTime fromDate,
      @Param("toDate") LocalDateTime toDate,
      @Param("lastDate") LocalDateTime lastDate,
      @Param("lastId") Long lastId,
      Pageable pageable);
}
