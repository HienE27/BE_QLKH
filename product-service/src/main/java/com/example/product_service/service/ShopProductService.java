package com.example.product_service.service;

import com.example.product_service.dto.ProductDto;
import com.example.product_service.dto.ProductRequest;

import java.util.List;

public interface ShopProductService {

    List<ProductDto> getAll();

    ProductDto getById(Long id);

    ProductDto create(ProductRequest request);

    ProductDto update(Long id, ProductRequest request);

    void delete(Long id);
}
