package com.example.settings_cms_service.service;

import com.example.settings_cms_service.dto.PostCategoryRequest;
import com.example.settings_cms_service.entity.ShopPostCategory;

import java.util.List;

public interface PostCategoryService {
    List<ShopPostCategory> getAll();
    ShopPostCategory getById(Long id);
    ShopPostCategory create(PostCategoryRequest request);
    ShopPostCategory update(Long id, PostCategoryRequest request);
    void delete(Long id);
}
