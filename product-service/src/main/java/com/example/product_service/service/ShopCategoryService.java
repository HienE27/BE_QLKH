package com.example.product_service.service;

import com.example.product_service.dto.CategoryDto;
import com.example.product_service.dto.CategoryRequest;
import com.example.product_service.entity.ShopCategory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ShopCategoryService {

    List<CategoryDto> getAll();

    CategoryDto getById(Long id);

    CategoryDto create(CategoryRequest request);

    CategoryDto update(Long id, CategoryRequest request);

    void delete(Long id);

    //List<ShopCategory> findAll();

    Page<CategoryDto> search(String code, String name, Pageable pageable);
}
