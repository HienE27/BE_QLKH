package com.example.inventory_service.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryCheckDetailDto {
    private Long id;
    private Long productId;
    private String productCode;
    private String productName;
    private String unit;

    private Integer systemQuantity; // Số lượng hệ thống
    private Integer actualQuantity; // Số lượng thực tế
    private Integer differenceQuantity; // Chênh lệch

    private BigDecimal unitPrice;
    private BigDecimal totalValue; // Giá trị chênh lệch

    private String note;
}
