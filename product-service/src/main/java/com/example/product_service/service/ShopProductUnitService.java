package com.example.product_service.service;

import com.example.product_service.dto.UnitDto;
import com.example.product_service.dto.UnitRequest;

import java.util.List;

public interface ShopProductUnitService {

    List<UnitDto> getAll();

    UnitDto getById(Long id);

    UnitDto create(UnitRequest request);

    UnitDto update(Long id, UnitRequest request);

    void delete(Long id);
}


