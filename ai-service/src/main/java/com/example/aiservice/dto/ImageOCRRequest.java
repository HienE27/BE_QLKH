package com.example.aiservice.dto;

import lombok.Data;

@Data
public class ImageOCRRequest {
    private String imageBase64; // Base64 encoded image
    private String imageUrl; // Hoặc URL của ảnh
    private String documentType; // INVOICE, RECEIPT, PRODUCT_LIST
}

