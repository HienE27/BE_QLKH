package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockOptimizationResponse {
    private List<ProductOptimization> optimizations;
    private List<WarehouseRecommendation> warehouseRecommendations;
    private List<CategoryOptimization> categoryOptimizations;
    private String summary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductOptimization {
        private Long productId;
        private String productCode;
        private String productName;
        private Integer currentStock;
        private Integer minStock; // Mức tồn tối thiểu đề xuất
        private Integer maxStock; // Mức tồn tối đa đề xuất
        private Integer optimalReorderQuantity; // Số lượng nhập lại tối ưu
        private String reasoning; // Lý do đề xuất
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WarehouseRecommendation {
        private Long productId;
        private String productCode;
        private String productName;
        private Long recommendedStoreId;
        private String recommendedStoreName;
        private String reasoning; // Lý do nên chứa ở kho này
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryOptimization {
        private String categoryName;
        private List<String> recommendations; // Gợi ý điều chỉnh danh mục
        private String analysis;
    }
}
