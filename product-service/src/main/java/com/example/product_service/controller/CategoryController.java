package com.example.product_service.controller;

import com.example.product_service.common.ApiResponse;
import com.example.product_service.entity.ShopCategory;
import com.example.product_service.service.ShopCategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final ShopCategoryService service;

    public CategoryController(ShopCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ShopCategory>> list() {
        return ApiResponse.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ShopCategory> get(@PathVariable Long id) {
        return ApiResponse.ok(service.getById(id));
    }

    @PostMapping
    public ApiResponse<ShopCategory> create(@RequestBody ShopCategory category) {
        return ApiResponse.ok("Created", service.create(category));
    }

    @PutMapping("/{id}")
    public ApiResponse<ShopCategory> update(@PathVariable Long id,
                                            @RequestBody ShopCategory category) {
        return ApiResponse.ok("Updated", service.update(id, category));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}
