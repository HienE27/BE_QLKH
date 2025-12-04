package com.example.product_service.service.impl;

import com.example.product_service.dto.UnitDto;
import com.example.product_service.dto.UnitRequest;
import com.example.product_service.entity.ShopProductUnit;
import com.example.product_service.exception.NotFoundException;
import com.example.product_service.repository.ShopProductRepository;
import com.example.product_service.repository.ShopProductUnitRepository;
import com.example.product_service.service.ShopProductUnitService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class ShopProductUnitServiceImpl implements ShopProductUnitService {

    private final ShopProductUnitRepository unitRepository;
    private final ShopProductRepository productRepository;

    public ShopProductUnitServiceImpl(ShopProductUnitRepository unitRepository,
            ShopProductRepository productRepository) {
        this.unitRepository = unitRepository;
        this.productRepository = productRepository;
    }

    @Override
    public List<UnitDto> getAll() {
        return unitRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public UnitDto getById(Long id) {
        ShopProductUnit unit = unitRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Unit not found: " + id));
        return toDto(unit);
    }

    @Override
    @Transactional
    public UnitDto create(UnitRequest request) {
        String normalizedName = normalizeName(request.getName());
        ensureUniqueName(normalizedName, null);

        ShopProductUnit unit = new ShopProductUnit();
        unit.setName(normalizedName);
        unit.setDescription(request.getDescription());
        unit.setActive(request.getActive() != null ? request.getActive() : Boolean.TRUE);
        unit.setCreatedAt(new Date());
        unit.setUpdatedAt(new Date());

        return toDto(unitRepository.save(unit));
    }

    @Override
    @Transactional
    public UnitDto update(Long id, UnitRequest request) {
        ShopProductUnit unit = unitRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Unit not found: " + id));

        String normalizedName = normalizeName(request.getName());
        ensureUniqueName(normalizedName, id);

        unit.setName(normalizedName);
        unit.setDescription(request.getDescription());

        if (request.getActive() != null) {
            unit.setActive(request.getActive());
        }

        unit.setUpdatedAt(new Date());

        return toDto(unitRepository.save(unit));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ShopProductUnit unit = unitRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Unit not found: " + id));

        if (productRepository.existsByUnitId(id)) {
            throw new IllegalStateException("Không thể xóa đơn vị đang được sử dụng");
        }

        unitRepository.delete(unit);
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Tên đơn vị không được để trống");
        }
        return name.trim();
    }

    private void ensureUniqueName(String name, Long excludeId) {
        boolean exists = excludeId == null
                ? unitRepository.existsByNameIgnoreCase(name)
                : unitRepository.existsByNameIgnoreCaseAndIdNot(name, excludeId);

        if (exists) {
            throw new IllegalArgumentException("Tên đơn vị đã tồn tại");
        }
    }

    private UnitDto toDto(ShopProductUnit unit) {
        UnitDto dto = new UnitDto();
        dto.setId(unit.getId());
        dto.setName(unit.getName());
        dto.setDescription(unit.getDescription());
        dto.setActive(unit.getActive());
        dto.setCreatedAt(unit.getCreatedAt());
        dto.setUpdatedAt(unit.getUpdatedAt());
        return dto;
    }
}


