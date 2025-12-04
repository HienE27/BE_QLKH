package com.example.product_service.controller;

import com.example.product_service.common.ApiResponse;
import com.example.product_service.dto.UnitDto;
import com.example.product_service.dto.UnitRequest;
import com.example.product_service.service.ShopProductUnitService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/units")
public class ShopProductUnitController {

    private final ShopProductUnitService unitService;

    public ShopProductUnitController(ShopProductUnitService unitService) {
        this.unitService = unitService;
    }

    @GetMapping
    public ApiResponse<List<UnitDto>> getAll() {
        return ApiResponse.ok(unitService.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<UnitDto> getById(@PathVariable Long id) {
        return ApiResponse.ok(unitService.getById(id));
    }

    @PostMapping
    public ApiResponse<UnitDto> create(@RequestBody UnitRequest request) {
        return ApiResponse.ok("Created", unitService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<UnitDto> update(@PathVariable Long id, @RequestBody UnitRequest request) {
        return ApiResponse.ok("Updated", unitService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        unitService.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}


