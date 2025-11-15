package com.example.inventory_service.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ImportDetailDto {

    private Long id;
    private Long productId;
    private Integer quantity;
    private BigDecimal unitPrice;
}
