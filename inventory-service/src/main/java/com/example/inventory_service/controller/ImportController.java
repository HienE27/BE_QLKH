package com.example.inventory_service.controller;

import com.example.inventory_service.common.ApiResponse;
import com.example.inventory_service.dto.SupplierImportDto;
import com.example.inventory_service.dto.SupplierImportRequest;
import com.example.inventory_service.service.ImportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService service;

    public ImportController(ImportService service) {
        this.service = service;
    }

    // ================= SEARCH =====================
    @GetMapping
    public ApiResponse<Page<SupplierImportDto>> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long storeId,
            Pageable pageable) {
        Page<SupplierImportDto> data = service.search(status, code, from, to, supplierId, storeId, pageable);
        return ApiResponse.ok(data);
    }

    // ================= DETAIL =====================
    @GetMapping("/{id}")
    public ApiResponse<SupplierImportDto> getById(@PathVariable Long id) {
        SupplierImportDto dto = service.getById(id);
        return ApiResponse.ok(dto);
    }

    // ================= CREATE =====================
    @PostMapping
    public ApiResponse<SupplierImportDto> create(
            @RequestBody SupplierImportRequest request) {
        SupplierImportDto dto = service.create(request);
        return ApiResponse.ok("Created", dto);
    }

    // ================= UPDATE =====================
    @PutMapping("/{id}")
    public ApiResponse<SupplierImportDto> update(
            @PathVariable Long id,
            @RequestBody SupplierImportRequest request) {
        SupplierImportDto dto = service.update(id, request);
        return ApiResponse.ok("Updated", dto);
    }

    // ================= CONFIRM (PENDING → IMPORTED) =====================
    @PostMapping("/{id}/confirm")
    public ApiResponse<SupplierImportDto> confirm(@PathVariable Long id) {
        SupplierImportDto dto = service.confirm(id);
        return ApiResponse.ok("Đã xác nhận nhập kho", dto);
    }

    // ================= CANCEL (PENDING → CANCELLED) =====================
    @PostMapping("/{id}/cancel")
    public ApiResponse<SupplierImportDto> cancel(@PathVariable Long id) {
        SupplierImportDto dto = service.cancel(id);
        return ApiResponse.ok("Đã hủy phiếu nhập", dto);
    }

    // ================= GET ALL =====================
    @GetMapping("/all")
    public ApiResponse<List<SupplierImportDto>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    // ================= GET BY STORE =====================
    @GetMapping("/by-store/{storeId}")
    public ApiResponse<List<SupplierImportDto>> getByStore(@PathVariable Long storeId) {
        return ApiResponse.ok(service.getByStore(storeId));
    }
}
