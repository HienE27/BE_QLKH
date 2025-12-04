package com.example.inventory_service.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryCheckDetailRequest {
    private Long productId;
    private Integer systemQuantity; // Số lượng hệ thống
    private Integer actualQuantity; // Số lượng thực tế kiểm đếm
    private BigDecimal unitPrice;
    private String note;
}
