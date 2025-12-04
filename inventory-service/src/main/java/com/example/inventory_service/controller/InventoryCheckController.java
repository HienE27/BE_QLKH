package com.example.inventory_service.controller;

import com.example.inventory_service.dto.InventoryCheckDto;
import com.example.inventory_service.dto.InventoryCheckRequest;
import com.example.inventory_service.service.InventoryCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controller quản lý kiểm kê kho
 */
@RestController
@RequestMapping("/api/inventory-checks")
@RequiredArgsConstructor
public class InventoryCheckController {

    private final InventoryCheckService service;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody InventoryCheckRequest request) {
        InventoryCheckDto dto = service.create(request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Tạo phiếu kiểm kê thành công",
                "data", dto));
    }

    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String checkCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long storeId,
            Pageable pageable) {
        Page<InventoryCheckDto> page = service.search(status, checkCode, fromDate, toDate, storeId, pageable);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        InventoryCheckDto dto = service.getById(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody InventoryCheckRequest request) {
        InventoryCheckDto dto = service.update(id, request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Cập nhật phiếu kiểm kê thành công",
                "data", dto));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        InventoryCheckDto dto = service.approve(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Duyệt phiếu kiểm kê thành công",
                "data", dto));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        InventoryCheckDto dto = service.reject(id, reason);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Từ chối phiếu kiểm kê thành công",
                "data", dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Xóa phiếu kiểm kê thành công"));
    }
}
