package com.example.inventory_service.dto;

import lombok.Data;

@Data
public class StoreDto {
    private Long id;
    private String code;
    private String name;
    private String description;
}
