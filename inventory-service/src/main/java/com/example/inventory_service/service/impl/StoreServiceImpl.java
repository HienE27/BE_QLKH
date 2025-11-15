package com.example.inventory_service.service.impl;

import com.example.inventory_service.dto.StoreDto;
import com.example.inventory_service.dto.StoreRequest;
import com.example.inventory_service.entity.ShopStore;
import com.example.inventory_service.exception.NotFoundException;
import com.example.inventory_service.repository.ShopStoreRepository;
import com.example.inventory_service.service.StoreService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class StoreServiceImpl implements StoreService {

    private final ShopStoreRepository repo;

    public StoreServiceImpl(ShopStoreRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<StoreDto> getAll() {
        return repo.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public StoreDto getById(Long id) {
        ShopStore s = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Store not found: " + id));
        return toDto(s);
    }

    @Override
    public StoreDto create(StoreRequest req) {
        ShopStore s = new ShopStore();
        s.setCode(req.getCode());
        s.setName(req.getName());
        s.setDescription(req.getDescription());
        s.setCreatedAt(new Date());
        s.setUpdatedAt(new Date());
        return toDto(repo.save(s));
    }

    @Override
    public StoreDto update(Long id, StoreRequest req) {
        ShopStore s = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Store not found: " + id));
        s.setCode(req.getCode());
        s.setName(req.getName());
        s.setDescription(req.getDescription());
        s.setUpdatedAt(new Date());
        return toDto(repo.save(s));
    }

    @Override
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("Store not found: " + id);
        }
        repo.deleteById(id);
    }

    private StoreDto toDto(ShopStore s) {
        StoreDto dto = new StoreDto();
        dto.setId(s.getId());
        dto.setCode(s.getCode());
        dto.setName(s.getName());
        dto.setDescription(s.getDescription());
        return dto;
    }
}
