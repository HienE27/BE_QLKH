package com.example.product_service.controller;

import com.example.product_service.common.ApiResponse;
import com.example.product_service.entity.ShopSupplier;
import com.example.product_service.service.ShopSupplierService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final ShopSupplierService service;

    public SupplierController(ShopSupplierService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ShopSupplier>> list() {
        return ApiResponse.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ShopSupplier> get(@PathVariable Long id) {
        return ApiResponse.ok(service.getById(id));
    }

    @PostMapping
    public ApiResponse<ShopSupplier> create(@RequestBody ShopSupplier supplier) {
        return ApiResponse.ok("Created", service.create(supplier));
    }

    @PutMapping("/{id}")
    public ApiResponse<ShopSupplier> update(@PathVariable Long id,
                                            @RequestBody ShopSupplier supplier) {
        return ApiResponse.ok("Updated", service.update(id, supplier));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}
