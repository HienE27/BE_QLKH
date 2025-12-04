package com.example.inventory_service.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ImportDetailRequest {
    private Long productId;
    private Long storeId; // Kho nhập cho dòng này (nếu null thì dùng kho mặc định từ header)
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountPercent; // Phần trăm chiết khấu (0-100)
}
