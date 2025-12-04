package com.example.settings_cms_service.controller;

import com.example.settings_cms_service.dto.PostCategoryRequest;
import com.example.settings_cms_service.entity.ShopPostCategory;
import com.example.settings_cms_service.service.PostCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/post-categories")
@RequiredArgsConstructor
public class PostCategoryController {

    private final PostCategoryService service;

    @GetMapping
    public List<ShopPostCategory> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public ShopPostCategory getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public ShopPostCategory create(@Valid @RequestBody PostCategoryRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ShopPostCategory update(@PathVariable Long id,
                                   @Valid @RequestBody PostCategoryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
