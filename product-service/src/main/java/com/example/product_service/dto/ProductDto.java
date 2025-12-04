package com.example.product_service.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
public class ProductDto {
    private Long id;
    private String code;
    private String name;
    private String shortDescription;
    private String image;
    private BigDecimal unitPrice;
    private String status;
    private Long categoryId;
    private Long supplierId; // NCC chính (tương thích ngược)
    private List<Long> supplierIds; // Danh sách NCC (many-to-many)
    private Long unitId;
    private String unitName;
    private String unit;
    private Date createdAt;
    private Date updatedAt;
    private String categoryName; // <── THÊM MỚI
}
