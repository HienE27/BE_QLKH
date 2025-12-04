package com.example.order_service.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryExportDetailRequest {

    private Long productId;
    private Integer quantity;
    private BigDecimal unitPrice;
}
