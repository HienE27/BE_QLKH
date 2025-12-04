package com.example.product_service.service;

import com.example.product_service.dto.ProductDto;
import com.example.product_service.dto.ProductRequest;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ShopProductService {

    List<ProductDto> getAll();

    ProductDto getById(Long id);

    ProductDto create(ProductRequest request);

    ProductDto update(Long id, ProductRequest request);

    void delete(Long id);

    Page<ProductDto> search(String code,
            String name,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable);
}
