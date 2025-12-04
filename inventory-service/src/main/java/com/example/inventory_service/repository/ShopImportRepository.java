package com.example.inventory_service.repository;

import com.example.inventory_service.entity.ShopImport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Date;

public interface ShopImportRepository extends JpaRepository<ShopImport, Long> {

  List<ShopImport> findByStoreId(Long storeId);

  @Query("""
      select i
      from ShopImport i
      where i.importType = 'SUPPLIER'
        and (:status is null or i.status = :status)
        and (:code is null or i.code like concat('%', :code, '%'))
        and (:fromDate is null or i.importsDate >= :fromDate)
        and (:toDate is null or i.importsDate < :toDate)
      order by i.importsDate desc
      """)
  List<ShopImport> searchSupplierImports(
      @Param("status") String status,
      @Param("code") String code,
      @Param("fromDate") Date fromDate,
      @Param("toDate") Date toDate);

  @Query("""
      select i
      from ShopImport i
      where i.importType = 'INTERNAL'
        and (:status is null or i.status = :status)
        and (:code is null or i.code like concat('%', :code, '%'))
        and (:fromDate is null or i.importsDate >= :fromDate)
        and (:toDate is null or i.importsDate < :toDate)
      order by i.importsDate desc
      """)
  List<ShopImport> searchInternalImports(
      @Param("status") String status,
      @Param("code") String code,
      @Param("fromDate") Date fromDate,
      @Param("toDate") Date toDate);

  @Query("""
      select i
      from ShopImport i
      where i.importType = 'STAFF'
        and (:status is null or i.status = :status)
        and (:code is null or i.code like concat('%', :code, '%'))
        and (:fromDate is null or i.importsDate >= :fromDate)
        and (:toDate is null or i.importsDate < :toDate)
      order by i.importsDate desc
      """)
  List<ShopImport> searchStaffImports(
      @Param("status") String status,
      @Param("code") String code,
      @Param("fromDate") Date fromDate,
      @Param("toDate") Date toDate);

  // Unified search - không filter theo type
  @Query("""
      select i
      from ShopImport i
      where (:status is null or i.status = :status)
        and (:code is null or i.code like concat('%', :code, '%'))
        and (:fromDate is null or i.importsDate >= :fromDate)
        and (:toDate is null or i.importsDate < :toDate)
      order by i.importsDate desc
      """)
  List<ShopImport> searchAllImports(
      @Param("status") String status,
      @Param("code") String code,
      @Param("fromDate") Date fromDate,
      @Param("toDate") Date toDate);

  // Unified search với pagination
  @Query("""
      select i
      from ShopImport i
      where (:status is null or i.status = :status)
        and (:code is null or i.code like concat('%', :code, '%'))
        and (:fromDate is null or i.importsDate >= :fromDate)
        and (:toDate is null or i.importsDate < :toDate)
        and (:supplierId is null or i.supplierId = :supplierId)
        and (:storeId is null or i.storeId = :storeId)
      order by i.importsDate desc
      """)
  Page<ShopImport> searchAllImports(
      @Param("status") String status,
      @Param("code") String code,
      @Param("fromDate") Date fromDate,
      @Param("toDate") Date toDate,
      @Param("supplierId") Long supplierId,
      @Param("storeId") Long storeId,
      Pageable pageable);
}
