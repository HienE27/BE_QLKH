package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SmartInventoryAlertResponse {
    private List<InventoryAlert> alerts;
    private String summary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryAlert {
        private String type; // "LOW_STOCK", "OUT_OF_STOCK", "SLOW_SELLING", "FAST_SELLING"
        private String severity; // "CRITICAL", "WARNING", "INFO"
        private Long productId;
        private String productCode;
        private String productName;
        private Integer currentStock;
        private Integer predictedDaysRemaining; // Số ngày còn lại dự đoán (nếu sắp hết)
        private Double avgDailySales; // Tốc độ bán trung bình/ngày
        private String message;
        private String recommendation;
    }
}
