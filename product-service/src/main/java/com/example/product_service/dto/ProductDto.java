package com.example.product_service.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class ProductDto {
    private Long id;
    private String code;
    private String name;
    private String shortDescription;
    private String image;
    private BigDecimal unitPrice;
    private Integer quantity;
    private String status;
    private Long categoryId;
    private Long supplierId;
    private Date createdAt;
    private Date updatedAt;

    private String categoryName;  // <── THÊM MỚI

}


