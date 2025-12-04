package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTurnoverResponse {
    private Double overallTurnoverRate; // Tỉ lệ vòng quay hàng hóa tổng thể
    private List<ProductTurnover> productTurnovers;
    private List<DeadStock> deadStocks;
    private List<OverstockedItem> overstockedItems;
    private String analysis;
    private List<String> recommendations;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductTurnover {
        private Long productId;
        private String productCode;
        private String productName;
        private Double turnoverRate; // Số lần vòng quay trong kỳ
        private Integer daysInStock; // Số ngày tồn kho trung bình
        private String efficiency; // "HIGH", "MEDIUM", "LOW"
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeadStock {
        private Long productId;
        private String productCode;
        private String productName;
        private Integer quantity;
        private Integer daysSinceLastSale; // Số ngày không bán được
        private Double totalValue; // Giá trị tồn kho
        private String recommendation; // Gợi ý xử lý
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverstockedItem {
        private Long productId;
        private String productCode;
        private String productName;
        private Integer currentStock;
        private Integer optimalStock;
        private Integer excessQuantity; // Số lượng thừa
        private String recommendation; // Gợi ý giảm giá/xử lý
    }
}
