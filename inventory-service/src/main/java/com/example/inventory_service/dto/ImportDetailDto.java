package com.example.inventory_service.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ImportDetailDto {
    private Long id;
    private Long productId;
    private Long storeId;
    private String storeName;
    private String storeCode;
    private String productCode;
    private String productName;
    private String unit;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountPercent; // Phần trăm chiết khấu (0-100)
}
