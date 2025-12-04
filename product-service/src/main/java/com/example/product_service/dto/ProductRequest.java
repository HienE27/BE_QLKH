package com.example.product_service.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductRequest {
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
}
