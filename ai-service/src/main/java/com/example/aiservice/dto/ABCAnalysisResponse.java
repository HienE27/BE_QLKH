package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ABCAnalysisResponse {
    private List<ProductCategory> categoryA; // 80% doanh thu
    private List<ProductCategory> categoryB; // 15% doanh thu
    private List<ProductCategory> categoryC; // 5% doanh thu
    private String analysis; // Phân tích AI
    private String recommendations; // Khuyến nghị
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductCategory {
        private String code;
        private String name;
        private double revenue;
        private double percentage;
        private int quantity;
    }
}

