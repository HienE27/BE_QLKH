package com.example.product_service.controller;

import com.example.product_service.common.ApiResponse;
import com.example.product_service.dto.CategoryDto;
import com.example.product_service.dto.CategoryRequest;
import com.example.product_service.service.ShopCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class ShopCategoryController {

    private final ShopCategoryService service;

    @GetMapping
    public ApiResponse<List<CategoryDto>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<CategoryDto> getById(@PathVariable Long id) {
        return ApiResponse.ok(service.getById(id));
    }

    @PostMapping
    public ApiResponse<CategoryDto> create(@RequestBody CategoryRequest req) {
        return ApiResponse.ok("Created", service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoryDto> update(@PathVariable Long id, @RequestBody CategoryRequest req) {
        return ApiResponse.ok("Updated", service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }

    @GetMapping("/search")
    public ApiResponse<Page<CategoryDto>> search(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(service.search(code, name, PageRequest.of(page, size)));
    }

}
