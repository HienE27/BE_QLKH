package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemandForecastResponse {
    private List<ForecastItem> forecasts;
    private String summary;
    private String analysis;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForecastItem {
        private Long productId;
        private String productCode;
        private String productName;
        private Integer currentStock;
        private Integer predictedDaysUntilReorder; // Số ngày dự đoán cần nhập lại
        private Integer recommendedQuantity; // Số lượng nhập đề xuất
        private Integer optimalStockLevel; // Mức tồn tối ưu (safety stock)
        private Double confidence; // Độ tin cậy dự đoán (0-1)
        private String reasoning; // Lý do dự đoán
    }
}
