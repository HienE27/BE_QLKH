package com.example.inventory_service.service;

import com.example.inventory_service.dto.StoreDto;
import com.example.inventory_service.dto.StoreRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StoreService {

    List<StoreDto> getAll();

    Page<StoreDto> getAll(Pageable pageable);

    StoreDto getById(Long id);

    StoreDto create(StoreRequest req);

    StoreDto update(Long id, StoreRequest req);

    void delete(Long id);

    Page<StoreDto> search(String code, String name, Pageable pageable);
}
