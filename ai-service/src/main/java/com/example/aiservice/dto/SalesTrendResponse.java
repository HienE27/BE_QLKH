package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesTrendResponse {
    private String period; // WEEKLY, MONTHLY, QUARTERLY
    private List<TrendData> trendData;
    private String trend; // INCREASING, DECREASING, STABLE
    private Double growthRate;
    private String analysis;
    private String forecast;
    private List<String> topProducts;
    private List<String> recommendations;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendData {
        private String label; // "Tuần 1", "Tháng 1"
        private Double revenue;
        private Integer orders;
        private Double growth; // % so với kỳ trước
    }
}

