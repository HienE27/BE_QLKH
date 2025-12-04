package com.example.settings_cms_service.service;

import com.example.settings_cms_service.dto.PostRequest;
import com.example.settings_cms_service.entity.ShopPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostService {
    Page<ShopPost> search(String status, Long categoryId, String keyword, Pageable pageable);
    ShopPost getById(Long id);
    ShopPost create(PostRequest request);
    ShopPost update(Long id, PostRequest request);
    void delete(Long id);
}
