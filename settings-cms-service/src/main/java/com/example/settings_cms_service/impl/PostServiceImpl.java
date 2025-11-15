package com.example.settings_cms_service.impl;

import com.example.settings_cms_service.dto.PostRequest;
import com.example.settings_cms_service.entity.ShopPost;
import com.example.settings_cms_service.entity.ShopPostCategory;
import com.example.settings_cms_service.repository.ShopPostCategoryRepository;
import com.example.settings_cms_service.repository.ShopPostRepository;
import com.example.settings_cms_service.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final ShopPostRepository postRepo;
    private final ShopPostCategoryRepository cateRepo;

    @Override
    public Page<ShopPost> search(String status, Long categoryId, String keyword, Pageable pageable) {
        if (StringUtils.hasText(keyword)) {
            return postRepo.findByTitleContainingIgnoreCase(keyword, pageable);
        }
        if (categoryId != null) {
            ShopPostCategory cate = cateRepo.findById(categoryId)
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            if (StringUtils.hasText(status)) {
                return postRepo.findByCategoryAndStatus(cate, status, pageable);
            }
        }
        if (StringUtils.hasText(status)) {
            return postRepo.findByStatus(status, pageable);
        }
        return postRepo.findAll(pageable);
    }

    @Override
    public ShopPost getById(Long id) {
        return postRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));
    }

    @Override
    public ShopPost create(PostRequest request) {
        ShopPostCategory cate = cateRepo.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        ShopPost post = ShopPost.builder()
                .title(request.getTitle())
                .slug(StringUtils.hasText(request.getSlug())
                        ? request.getSlug()
                        : generateSlug(request.getTitle()))
                .content(request.getContent())
                .excerpt(request.getExcerpt())
                .status(request.getStatus())
                .image(request.getImage())
                .category(cate)
                .userId(request.getUserId())
                .build();
        return postRepo.save(post);
    }

    @Override
    public ShopPost update(Long id, PostRequest request) {
        ShopPost post = getById(id);

        if (request.getTitle() != null) post.setTitle(request.getTitle());
        if (request.getSlug() != null) post.setSlug(request.getSlug());
        post.setContent(request.getContent());
        post.setExcerpt(request.getExcerpt());
        post.setStatus(request.getStatus());
        post.setImage(request.getImage());

        if (request.getCategoryId() != null) {
            ShopPostCategory cate = cateRepo.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            post.setCategory(cate);
        }
        if (request.getUserId() != null) post.setUserId(request.getUserId());

        return postRepo.save(post);
    }

    @Override
    public void delete(Long id) {
        postRepo.deleteById(id);
    }

    private String generateSlug(String title) {
        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-");
    }
}
