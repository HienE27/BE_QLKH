package com.example.aiservice.dto;

import lombok.Data;

@Data
public class PriceSuggestionRequest {
    private String productCode;
    private String productName;
    private Double currentPrice;
    private Double costPrice;
    private Integer currentStock;
    private Integer avgDailySales;
    private Integer daysInStock; // Số ngày tồn kho
}

