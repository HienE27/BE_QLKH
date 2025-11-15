package com.example.settings_cms_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PostRequest {
    @NotBlank
    private String title;
    private String slug;
    private String content;
    private String excerpt;
    private String status;
    private String image;
    private Long categoryId;
    private Long userId;
}
