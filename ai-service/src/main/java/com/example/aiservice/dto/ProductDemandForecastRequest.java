package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDemandForecastRequest {
    private Long productId;
    private Integer days; // Số ngày để dự đoán (mặc định 30)
}
