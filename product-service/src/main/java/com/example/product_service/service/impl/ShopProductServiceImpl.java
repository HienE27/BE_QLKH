package com.example.product_service.service.impl;

import com.example.product_service.dto.ProductDto;
import com.example.product_service.dto.ProductRequest;
import com.example.product_service.entity.ShopProduct;
import com.example.product_service.exception.NotFoundException;
import com.example.product_service.repository.ShopProductRepository;
import com.example.product_service.service.ShopProductService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class ShopProductServiceImpl implements ShopProductService {

    private final ShopProductRepository repo;

    public ShopProductServiceImpl(ShopProductRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<ProductDto> getAll() {
        return repo.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public ProductDto getById(Long id) {
        ShopProduct p = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
        return toDto(p);
    }

    @Override
    public ProductDto create(ProductRequest request) {
        ShopProduct p = new ShopProduct();
        applyRequestToEntity(request, p);
        p.setCreatedAt(new Date());
        p.setUpdatedAt(new Date());
        return toDto(repo.save(p));
    }

    @Override
    public ProductDto update(Long id, ProductRequest request) {
        ShopProduct p = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));

        applyRequestToEntity(request, p);
        p.setUpdatedAt(new Date());
        return toDto(repo.save(p));
    }

    @Override
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("Product not found: " + id);
        }
        repo.deleteById(id);
    }

    // --------- mapping helpers ---------
    private ProductDto toDto(ShopProduct p) {
        ProductDto dto = new ProductDto();
        dto.setId(p.getId());
        dto.setCode(p.getCode());
        dto.setName(p.getName());
        dto.setShortDescription(p.getShortDescription());
        dto.setImage(p.getImage());
        dto.setUnitPrice(p.getUnitPrice());
        dto.setQuantity(p.getQuantity());
        dto.setStatus(p.getStatus());
        dto.setCategoryId(p.getCategoryId());
        dto.setSupplierId(p.getSupplierId());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());
        return dto;
    }

    private void applyRequestToEntity(ProductRequest req, ShopProduct p) {
        p.setCode(req.getCode());
        p.setName(req.getName());
        p.setShortDescription(req.getShortDescription());
        p.setImage(req.getImage());
        p.setUnitPrice(req.getUnitPrice());
        p.setQuantity(req.getQuantity());
        p.setStatus(req.getStatus());
        p.setCategoryId(req.getCategoryId());
        p.setSupplierId(req.getSupplierId());
    }
}
