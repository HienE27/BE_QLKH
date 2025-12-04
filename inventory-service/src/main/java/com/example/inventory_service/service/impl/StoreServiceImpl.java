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
        // Tự động tạo mã nếu không có trong request
        if (req.getCode() == null || req.getCode().isBlank()) {
            s.setCode(generateStoreCode());
        } else {
            s.setCode(req.getCode());
        }
        s.setName(req.getName());
        s.setDescription(req.getDescription());
        s.setCreatedAt(new Date());
        s.setUpdatedAt(new Date());
        return toDto(repo.save(s));
    }

    /**
     * Tự động tạo mã kho hàng: KO + 5 số (ví dụ: KO00001)
     */
    private String generateStoreCode() {
        String prefix = "KO";
        List<ShopStore> existing = repo.findByCodeStartingWith(prefix);
        long maxNumber = 0;
        
        for (ShopStore s : existing) {
            if (s.getCode() != null && s.getCode().length() >= 3) {
                try {
                    String numberPart = s.getCode().substring(2);
                    long num = Long.parseLong(numberPart);
                    if (num > maxNumber) {
                        maxNumber = num;
                    }
                } catch (NumberFormatException e) {
                    // Bỏ qua nếu không parse được số
                }
            }
        }
        
        long nextNumber = maxNumber + 1;
        return prefix + String.format("%05d", nextNumber);
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
