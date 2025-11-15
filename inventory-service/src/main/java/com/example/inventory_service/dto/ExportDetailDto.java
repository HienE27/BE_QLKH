package com.example.inventory_service.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ExportDetailDto {
    private Long id;
    private Long importDetailId;
    private Long productId;
    private Integer quantity;
    private BigDecimal unitPrice;
}
