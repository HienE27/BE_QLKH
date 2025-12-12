package com.example.inventory_service.controller;

import com.example.inventory_service.common.ApiResponse;
import com.example.inventory_service.dto.SupplierImportDto;
import com.example.inventory_service.dto.SupplierImportRequest;
import com.example.inventory_service.service.ImportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    /**
     * @deprecated Use {@link #searchPaged(String, String, LocalDate, LocalDate, int, int, String, String)} instead.
     * This endpoint returns a limited list (max 1000 records) and may not return all results.
     * For better performance and pagination, use /api/imports/search with page and size parameters.
     */
    @Deprecated
    @GetMapping
    public ApiResponse<List<SupplierImportDto>> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        // Validate status if provided
        if (status != null && !status.isBlank()) {
            try {
                com.example.inventory_service.entity.ImportStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ApiResponse.fail("Invalid status: " + status + ". Valid values: " + 
                    java.util.Arrays.toString(com.example.inventory_service.entity.ImportStatus.values()));
            }
        }
        List<SupplierImportDto> data = service.search(status, code, from, to);
        return ApiResponse.ok(data);
    }

    @GetMapping("/search")
    public ApiResponse<Page<SupplierImportDto>> searchPaged(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "date") String sortField,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        // Validate status if provided
        if (status != null && !status.isBlank()) {
            try {
                com.example.inventory_service.entity.ImportStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ApiResponse.fail("Invalid status: " + status + ". Valid values: " + 
                    java.util.Arrays.toString(com.example.inventory_service.entity.ImportStatus.values()));
            }
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<SupplierImportDto> data = service.searchPaged(status, code, from, to, sortField, sortDir, pageable);
        return ApiResponse.ok(data);
    }

    // ================= DETAIL =====================
    @GetMapping("/{id:\\d+}")
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

    // ================= APPROVE (PENDING → APPROVED) =====================
    @PostMapping("/{id}/approve")
    public ApiResponse<SupplierImportDto> approve(@PathVariable Long id) {
        SupplierImportDto dto = service.approve(id);
        return ApiResponse.ok("Đã duyệt phiếu nhập (chờ nhập kho)", dto);
    }

    // ================= CONFIRM (APPROVED → IMPORTED) =====================
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

    // ================= REJECT (PENDING → REJECTED) =====================
    @PostMapping("/{id}/reject")
    public ApiResponse<SupplierImportDto> reject(@PathVariable Long id) {
        SupplierImportDto dto = service.reject(id);
        return ApiResponse.ok("Đã từ chối phiếu nhập", dto);
    }

    // ================= GET ALL =====================
    /**
     * @deprecated Use paginated version {@link #getAllPaged(int, int)} instead.
     * This endpoint returns a limited list (max 100 records) and may not return all results.
     */
    @Deprecated
    @GetMapping("/all")
    public ApiResponse<List<SupplierImportDto>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @GetMapping("/all/paged")
    public ApiResponse<Page<SupplierImportDto>> getAllPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SupplierImportDto> data = service.getAll(pageable);
        return ApiResponse.ok(data);
    }

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }

    // ================= GET BY STORE =====================
    /**
     * @deprecated Use paginated version {@link #getByStorePaged(Long, int, int)} instead.
     * This endpoint returns a limited list (max 100 records) and may not return all results.
     */
    @Deprecated
    @GetMapping("/by-store/{storeId}")
    public ApiResponse<List<SupplierImportDto>> getByStore(@PathVariable Long storeId) {
        return ApiResponse.ok(service.getByStore(storeId));
    }

    @GetMapping("/by-store/{storeId}/paged")
    public ApiResponse<Page<SupplierImportDto>> getByStorePaged(
            @PathVariable Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SupplierImportDto> data = service.getByStore(storeId, pageable);
        return ApiResponse.ok(data);
    }
}
