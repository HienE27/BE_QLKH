package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageOCRResponse {
    private String documentType;
    private String supplierName;
    private String invoiceNumber;
    private String invoiceDate;
    private List<ExtractedItem> items;
    private Double totalAmount;
    private String rawText;
    private Double confidence;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedItem {
        private String name;
        private String code;
        private Integer quantity;
        private Double unitPrice;
        private Double totalPrice;
        private String unit;
    }
}

