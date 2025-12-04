package com.example.product_service.dto;

import lombok.Data;

@Data
public class CategoryRequest {
    private String code;
    private String name;
    private String image;
    private String description;
}
