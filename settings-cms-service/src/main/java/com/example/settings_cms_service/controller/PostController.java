package com.example.settings_cms_service.controller;

import com.example.settings_cms_service.dto.PostRequest;
import com.example.settings_cms_service.entity.ShopPost;
import com.example.settings_cms_service.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public Page<ShopPost> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return postService.search(status, categoryId, keyword, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public ShopPost detail(@PathVariable Long id) {
        return postService.getById(id);
    }

    @PostMapping
    public ShopPost create(@Valid @RequestBody PostRequest request) {
        return postService.create(request);
    }

    @PutMapping("/{id}")
    public ShopPost update(@PathVariable Long id,
                           @RequestBody PostRequest request) {
        return postService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        postService.delete(id);
    }
}
