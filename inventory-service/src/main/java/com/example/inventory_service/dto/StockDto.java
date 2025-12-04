package com.example.inventory_service.dto;

import lombok.Data;

@Data
public class StockDto {
    private Long productId;
    private Integer importedQty;   // tổng nhập
    private Integer exportedQty;   // tổng xuất
    private Integer currentQty;    // tồn = nhập - xuất
}
