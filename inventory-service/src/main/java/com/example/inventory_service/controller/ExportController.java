package com.example.inventory_service.controller;

import com.example.inventory_service.common.ApiResponse;
import com.example.inventory_service.dto.ExportDto;
import com.example.inventory_service.dto.ExportRequest;
import com.example.inventory_service.service.ExportService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exports")
public class ExportController {

    private final ExportService service;

    public ExportController(ExportService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<ExportDto> create(@RequestBody ExportRequest req) {
        return ApiResponse.ok("Created", service.create(req));
    }

    @GetMapping
    public ApiResponse<List<ExportDto>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ExportDto> getById(@PathVariable Long id) {
        return ApiResponse.ok(service.getById(id));
    }

    @GetMapping("/by-store/{storeId}")
    public ApiResponse<List<ExportDto>> getByStore(@PathVariable Long storeId) {
        return ApiResponse.ok(service.getByStore(storeId));
    }

    @GetMapping("/by-order/{orderId}")
    public ApiResponse<List<ExportDto>> getByOrder(@PathVariable Long orderId) {
        return ApiResponse.ok(service.getByOrder(orderId));
    }
}
