package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDemandForecastResponse {
    private Long productId;
    private String productCode;
    private String productName;
    private Integer currentStock;
    private Double avgDailySales; // Tốc độ bán trung bình/ngày
    private Integer predictedDaysUntilStockOut; // Số ngày dự đoán sẽ hết hàng
    private Integer recommendedReorderQuantity; // Số lượng nhập đề xuất
    private Integer optimalStockLevel; // Mức tồn tối ưu
    private Double confidence; // Độ tin cậy (0-1)
    private String detailedAnalysis; // Phân tích chi tiết từ AI
    private String recommendations; // Đề xuất hành động
    private List<DailyForecast> dailyForecasts; // Dự đoán theo từng ngày

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyForecast {
        private Integer day; // Ngày thứ bao nhiêu (1, 2, 3, ...)
        private Integer predictedStock; // Tồn kho dự đoán
        private Integer predictedSales; // Số lượng bán dự đoán
        private String date; // Ngày cụ thể (yyyy-MM-dd)
    }
}
