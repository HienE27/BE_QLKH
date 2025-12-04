package com.example.aiservice.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class ReceiptOCRRequest {
    private String imageUrl; // URL của ảnh đã upload (optional)

    @NotBlank(message = "Receipt type is required")
    private String receiptType; // "IMPORT" hoặc "EXPORT"

    private String imageBase64; // Base64 encoded image (optional, nếu không có URL)

    // Validation: phải có ít nhất một trong hai (imageUrl hoặc imageBase64)
    public boolean isValid() {
        return (imageUrl != null && !imageUrl.isBlank())
                || (imageBase64 != null && !imageBase64.isBlank());
    }
}
