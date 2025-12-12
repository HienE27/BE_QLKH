package com.example.inventory_service.controller;

import com.example.inventory_service.common.ApiResponse;
import com.example.inventory_service.dto.SupplierExportDto;
import com.example.inventory_service.dto.SupplierExportRequest;
import com.example.inventory_service.entity.ExportStatus;
import com.example.inventory_service.service.ExportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/exports")
public class ExportController {

    private final ExportService service;

    public ExportController(ExportService service) {
        this.service = service;
    }

    // ================= SEARCH =====================
    @GetMapping
    public ApiResponse<Page<SupplierExportDto>> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        ExportStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = ExportStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ApiResponse.fail("Invalid status: " + status + ". Valid values: " + 
                    java.util.Arrays.toString(ExportStatus.values()));
            }
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<SupplierExportDto> data = service.search(statusEnum, code, from, to, pageable);
        return ApiResponse.ok(data);
    }

    @GetMapping("/search")
    public ApiResponse<Page<SupplierExportDto>> searchPaged(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "date") String sortField,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        ExportStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = ExportStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ApiResponse.fail("Invalid status: " + status + ". Valid values: " + 
                    java.util.Arrays.toString(ExportStatus.values()));
            }
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<SupplierExportDto> data = service.searchPaged(statusEnum, code, from, to, sortField, sortDir, pageable);
        return ApiResponse.ok(data);
    }

    @GetMapping("/search-keyset")
    public ApiResponse<Page<SupplierExportDto>> searchKeyset(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lastDate,
            @RequestParam(required = false) Long lastId,
            @RequestParam(defaultValue = "10") int size) {
        ExportStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = ExportStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ApiResponse.fail("Invalid status: " + status + ". Valid values: " + 
                    java.util.Arrays.toString(ExportStatus.values()));
            }
        }
        Pageable pageable = PageRequest.of(0, size);
        Page<SupplierExportDto> data = service.searchKeyset(statusEnum, code, from, to, lastDate, lastId, pageable);
        return ApiResponse.ok(data);
    }

    // ================= DETAIL =====================
    @GetMapping("/{id:\\d+}")
    public ApiResponse<SupplierExportDto> getById(@PathVariable Long id) {
        SupplierExportDto dto = service.getById(id);
        return ApiResponse.ok(dto);
    }

    // ================= CREATE =====================
    @PostMapping
    public ApiResponse<SupplierExportDto> create(
            @RequestBody SupplierExportRequest request) {
        SupplierExportDto dto = service.create(request);
        return ApiResponse.ok("Created", dto);
    }

    // ================= UPDATE =====================
    @PutMapping("/{id}")
    public ApiResponse<SupplierExportDto> update(
            @PathVariable Long id,
            @RequestBody SupplierExportRequest request) {
        SupplierExportDto dto = service.update(id, request);
        return ApiResponse.ok("Updated", dto);
    }

    // ================= APPROVE (PENDING → APPROVED) =====================
    @PostMapping("/{id}/approve")
    public ApiResponse<SupplierExportDto> approve(@PathVariable Long id) {
        SupplierExportDto dto = service.approve(id);
        return ApiResponse.ok("Đã duyệt phiếu xuất (chờ xuất kho)", dto);
    }

    // ================= CONFIRM (APPROVED → EXPORTED) =====================
    @PostMapping("/{id}/confirm")
    public ApiResponse<SupplierExportDto> confirm(@PathVariable Long id) {
        SupplierExportDto dto = service.confirm(id);
        return ApiResponse.ok("Đã xác nhận xuất kho", dto);
    }

    // ================= CANCEL (PENDING → CANCELLED) =====================
    @PostMapping("/{id}/cancel")
    public ApiResponse<SupplierExportDto> cancel(@PathVariable Long id) {
        SupplierExportDto dto = service.cancel(id);
        return ApiResponse.ok("Đã hủy phiếu xuất", dto);
    }

    // ================= REJECT (PENDING → REJECTED) =====================
    @PostMapping("/{id}/reject")
    public ApiResponse<SupplierExportDto> reject(@PathVariable Long id) {
        SupplierExportDto dto = service.reject(id);
        return ApiResponse.ok("Đã từ chối phiếu xuất", dto);
    }

    // ================= GET ALL =====================
    @GetMapping("/all")
    public ApiResponse<Page<SupplierExportDto>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(service.getAll(pageable));
    }

    // ================= GET BY STORE =====================
    @GetMapping("/by-store/{storeId}")
    public ApiResponse<Page<SupplierExportDto>> getByStore(
            @PathVariable Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(service.getByStore(storeId, pageable));
    }

    // ================= GET BY ORDER =====================
    @GetMapping("/by-order/{orderId}")
    public ApiResponse<Page<SupplierExportDto>> getByOrder(
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(service.getByOrder(orderId, pageable));
    }
}
