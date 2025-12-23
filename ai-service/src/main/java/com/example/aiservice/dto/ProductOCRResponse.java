package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductOCRResponse {
    private String name; // Tên sản phẩm
    private String code; // Mã sản phẩm (nếu có)
    private Double price; // Giá sản phẩm (nếu có)
    private String description; // Mô tả sản phẩm (nếu có)
    private String category; // Tên danh mục (nếu có)
    private String unit; // Đơn vị tính (nếu có)
    private String brand; // Thương hiệu (nếu có)
    private String specifications; // Thông số kỹ thuật (nếu có)
    private String supplier; // Tên nhà cung cấp (nếu có trong ảnh)
    private String warehouse; // Tên kho hàng (nếu có trong ảnh)
    
    // Metadata
    private String rawText; // Text thô từ OCR
    private Double confidence; // Độ tin cậy tổng thể
    private Double nameConfidence; // Độ tin cậy tên
    private Double codeConfidence; // Độ tin cậy mã
    private Double priceConfidence; // Độ tin cậy giá
    
    // Hỗ trợ đọc nhiều sản phẩm từ một ảnh
    private java.util.List<ProductItem> products; // Danh sách sản phẩm (nếu ảnh có nhiều sản phẩm)
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductItem {
        private String name;
        private String code;
        private Double price;
        private String description;
        private String category;
        private String unit;
        private String brand;
        private String specifications;
        private String supplier;
        private String warehouse;
    }
}

