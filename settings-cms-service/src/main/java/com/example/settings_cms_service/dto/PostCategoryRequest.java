package com.example.settings_cms_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PostCategoryRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    private String description;
    private String image;
}