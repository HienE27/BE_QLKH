package com.example.inventory_service.service;

import com.example.inventory_service.dto.SupplierExportDto;
import com.example.inventory_service.dto.SupplierExportRequest;
import com.example.inventory_service.entity.ExportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface ExportService {

    SupplierExportDto create(SupplierExportRequest req);

    Page<SupplierExportDto> search(ExportStatus status, String code, LocalDate from, LocalDate to, Pageable pageable);

    Page<SupplierExportDto> searchPaged(ExportStatus status,
                                        String code,
                                        LocalDate from,
                                        LocalDate to,
                                        String sortField,
                                        String sortDir,
                                        Pageable pageable);

    Page<SupplierExportDto> searchKeyset(ExportStatus status,
                                        String code,
                                        LocalDate from,
                                        LocalDate to,
                                        LocalDateTime lastDate,
                                        Long lastId,
                                        Pageable pageable);

    SupplierExportDto getById(Long id);

    SupplierExportDto update(Long id, SupplierExportRequest req);

    /**
     * Bước 1 (Manager): duyệt phiếu, chuyển PENDING -> APPROVED (chưa trừ kho)
     */
    SupplierExportDto approve(Long id);

    SupplierExportDto confirm(Long id);

    SupplierExportDto cancel(Long id);

    SupplierExportDto reject(Long id);

    Page<SupplierExportDto> getAll(Pageable pageable);

    Page<SupplierExportDto> getByStore(Long storeId, Pageable pageable);

    Page<SupplierExportDto> getByOrder(Long orderId, Pageable pageable);
}
