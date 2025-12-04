package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesInsightResponse {
    private RevenueAnalysis revenueAnalysis;
    private List<TopProduct> topProducts;
    private List<DecliningProduct> decliningProducts;
    private BestSellingHours bestSellingHours;
    private List<SeasonalProduct> seasonalProducts;
    private String overallAnalysis;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueAnalysis {
        private String trend; // "INCREASING", "DECREASING", "STABLE"
        private Double growthRate; // Tỷ lệ tăng trưởng (%)
        private String reason; // Lý do tăng/giảm
        private Double currentRevenue;
        private Double previousRevenue;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopProduct {
        private Long productId;
        private String productCode;
        private String productName;
        private Double revenue;
        private Integer quantitySold;
        private Integer rank;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DecliningProduct {
        private Long productId;
        private String productCode;
        private String productName;
        private Double revenueDecline; // % giảm
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BestSellingHours {
        private List<HourSales> hourlyData;
        private String peakHours; // Giờ bán tốt nhất
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourSales {
        private Integer hour; // 0-23
        private Double revenue;
        private Integer orderCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeasonalProduct {
        private Long productId;
        private String productCode;
        private String productName;
        private String season; // "SPRING", "SUMMER", "AUTUMN", "WINTER", "ALL_YEAR"
        private Double seasonalMultiplier; // Hệ số theo mùa
    }
}
