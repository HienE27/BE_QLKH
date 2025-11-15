package com.example.inventory_service.controller;

import com.example.inventory_service.common.ApiResponse;
import com.example.inventory_service.dto.StoreDto;
import com.example.inventory_service.dto.StoreRequest;
import com.example.inventory_service.service.StoreService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreService service;

    public StoreController(StoreService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<StoreDto>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<StoreDto> getById(@PathVariable Long id) {
        return ApiResponse.ok(service.getById(id));
    }

    @PostMapping
    public ApiResponse<StoreDto> create(@RequestBody StoreRequest req) {
        return ApiResponse.ok("Created", service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<StoreDto> update(@PathVariable Long id,
                                        @RequestBody StoreRequest req) {
        return ApiResponse.ok("Updated", service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}
