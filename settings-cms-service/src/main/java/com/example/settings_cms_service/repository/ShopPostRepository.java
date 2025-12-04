package com.example.settings_cms_service.repository;

import com.example.settings_cms_service.entity.ShopPost;
import com.example.settings_cms_service.entity.ShopPostCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopPostRepository extends JpaRepository<ShopPost, Long> {

    Page<ShopPost> findByStatus(String status, Pageable pageable);

    Page<ShopPost> findByCategoryAndStatus(ShopPostCategory category,
                                           String status,
                                           Pageable pageable);

    Page<ShopPost> findByTitleContainingIgnoreCase(String keyword,
                                                   Pageable pageable);
}