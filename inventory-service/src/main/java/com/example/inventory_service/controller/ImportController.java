package com.example.inventory_service.controller;

import com.example.inventory_service.common.ApiResponse;
import com.example.inventory_service.dto.ImportDto;
import com.example.inventory_service.dto.ImportRequest;
import com.example.inventory_service.service.ImportService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService service;

    public ImportController(ImportService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<ImportDto> create(@RequestBody ImportRequest req) {
        return ApiResponse.ok("Created", service.create(req));
    }

    @GetMapping
    public ApiResponse<List<ImportDto>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ImportDto> getById(@PathVariable Long id) {
        return ApiResponse.ok(service.getById(id));
    }

    @GetMapping("/by-store/{storeId}")
    public ApiResponse<List<ImportDto>> getByStore(@PathVariable Long storeId) {
        return ApiResponse.ok(service.getByStore(storeId));
    }
}
