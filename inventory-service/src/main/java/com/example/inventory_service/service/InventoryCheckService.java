package com.example.inventory_service.service;

import com.example.inventory_service.dto.InventoryCheckDto;
import com.example.inventory_service.dto.InventoryCheckRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface InventoryCheckService {

    InventoryCheckDto create(InventoryCheckRequest request);

    List<InventoryCheckDto> search(
            String status,
            String checkCode,
            LocalDate from,
            LocalDate to);

    Page<InventoryCheckDto> search(
            String status,
            String checkCode,
            LocalDate from,
            LocalDate to,
            Long storeId,
            Pageable pageable);

    InventoryCheckDto getById(Long id);

    InventoryCheckDto update(Long id, InventoryCheckRequest request);

    // Duyệt phiếu kiểm kê (PENDING → APPROVED)
    InventoryCheckDto approve(Long id);

    // Từ chối phiếu kiểm kê (PENDING → REJECTED)
    InventoryCheckDto reject(Long id, String reason);

    // Xóa phiếu kiểm kê (chỉ xóa được khi PENDING)
    void delete(Long id);
}
