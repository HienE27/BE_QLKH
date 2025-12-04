package com.example.product_service.dto;

import lombok.Data;

import java.util.Date;

@Data
public class UnitDto {
    private Long id;
    private String name;
    private String description;
    private Boolean active;
    private Date createdAt;
    private Date updatedAt;
}


