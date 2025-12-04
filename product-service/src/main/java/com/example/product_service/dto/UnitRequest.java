package com.example.product_service.dto;

import lombok.Data;

@Data
public class UnitRequest {
    private String name;
    private String description;
    private Boolean active;
}


