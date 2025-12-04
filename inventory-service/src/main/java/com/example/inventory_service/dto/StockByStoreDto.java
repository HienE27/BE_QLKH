package com.example.inventory_service.dto;

import lombok.Data;

@Data
public class StockByStoreDto {
    private Long productId;
    private Long storeId;
    private String storeName;
    private String storeCode;
    private Integer quantity;      // Số lượng tồn tại kho này
    private Integer minStock;
    private Integer maxStock;
}

