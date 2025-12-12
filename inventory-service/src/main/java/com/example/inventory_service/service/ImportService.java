package com.example.inventory_service.service;

import com.example.inventory_service.dto.SupplierImportDto;
import com.example.inventory_service.dto.SupplierImportRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface ImportService {

    SupplierImportDto create(SupplierImportRequest req);

    List<SupplierImportDto> search(String status, String code, LocalDate from, LocalDate to);

    Page<SupplierImportDto> searchPaged(String status,
                                        String code,
                                        LocalDate from,
                                        LocalDate to,
                                        String sortField,
                                        String sortDir,
                                        Pageable pageable);

    SupplierImportDto getById(Long id);

    SupplierImportDto update(Long id, SupplierImportRequest req);

    /**
     * Bước 1 (Manager): duyệt phiếu, chuyển PENDING -> APPROVED (chưa nhập kho)
     */
    SupplierImportDto approve(Long id);

    SupplierImportDto confirm(Long id);

    SupplierImportDto cancel(Long id);

    SupplierImportDto reject(Long id);

    List<SupplierImportDto> getAll();

    Page<SupplierImportDto> getAll(Pageable pageable);

    List<SupplierImportDto> getByStore(Long storeId);

    Page<SupplierImportDto> getByStore(Long storeId, Pageable pageable);
}
