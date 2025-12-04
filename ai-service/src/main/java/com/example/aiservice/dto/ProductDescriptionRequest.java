package com.example.aiservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProductDescriptionRequest {

    @NotBlank(message = "name is required")
    private String name;
}


