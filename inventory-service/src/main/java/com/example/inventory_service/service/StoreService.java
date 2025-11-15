package com.example.inventory_service.service;

import com.example.inventory_service.dto.StoreDto;
import com.example.inventory_service.dto.StoreRequest;

import java.util.List;

public interface StoreService {

    List<StoreDto> getAll();

    StoreDto getById(Long id);

    StoreDto create(StoreRequest req);

    StoreDto update(Long id, StoreRequest req);

    void delete(Long id);
}
