package com.example.inventory_service.dto;

import lombok.Data;

@Data
public class CreateStockRequest {
    private Long productId;
    private Long storeId;
    private Integer quantity;
    private Integer minStock;
    private Integer maxStock;
}
