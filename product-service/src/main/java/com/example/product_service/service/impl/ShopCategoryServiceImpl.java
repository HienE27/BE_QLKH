package com.example.product_service.service.impl;

import com.example.product_service.entity.ShopCategory;
import com.example.product_service.exception.NotFoundException;
import com.example.product_service.repository.ShopCategoryRepository;
import com.example.product_service.service.ShopCategoryService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShopCategoryServiceImpl implements ShopCategoryService {

    private final ShopCategoryRepository repo;

    public ShopCategoryServiceImpl(ShopCategoryRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<ShopCategory> findAll() {
        return repo.findAll();
    }

    @Override
    public ShopCategory getById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found with id = " + id));
    }

    @Override
    public ShopCategory create(ShopCategory category) {
        return repo.save(category);
    }

    @Override
    public ShopCategory update(Long id, ShopCategory category) {
        ShopCategory db = getById(id);
        db.setCode(category.getCode());
        db.setName(category.getName());
        db.setImage(category.getImage());
        db.setDescription(category.getDescription());
        db.setCreatedAt(category.getCreatedAt());
        db.setUpdatedAt(category.getUpdatedAt());
        return repo.save(db);
    }

    @Override
    public void delete(Long id) {
        ShopCategory db = getById(id);
        repo.delete(db);
    }
}
