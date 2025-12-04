package com.example.settings_cms_service.impl;

import com.example.settings_cms_service.dto.PostCategoryRequest;
import com.example.settings_cms_service.entity.ShopPostCategory;
import com.example.settings_cms_service.repository.ShopPostCategoryRepository;
import com.example.settings_cms_service.service.PostCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostCategoryServiceImpl implements PostCategoryService {

    private final ShopPostCategoryRepository repo;

    @Override
    public List<ShopPostCategory> getAll() {
        return repo.findAll();
    }

    @Override
    public ShopPostCategory getById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
    }

    @Override
    public ShopPostCategory create(PostCategoryRequest request) {
        ShopPostCategory cate = ShopPostCategory.builder()
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .image(request.getImage())
                .createdAt(LocalDateTime.now())
                .build();
        return repo.save(cate);
    }

    @Override
    public ShopPostCategory update(Long id, PostCategoryRequest request) {
        ShopPostCategory cate = getById(id);
        cate.setCode(request.getCode());
        cate.setName(request.getName());
        cate.setDescription(request.getDescription());
        cate.setImage(request.getImage());
        cate.setUpdatedAt(LocalDateTime.now());
        return repo.save(cate);
    }

    @Override
    public void delete(Long id) {
        repo.deleteById(id);
    }
}
