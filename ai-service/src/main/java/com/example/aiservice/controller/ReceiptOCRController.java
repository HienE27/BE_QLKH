package com.example.aiservice.controller;

import com.example.aiservice.common.ApiResponse;
import com.example.aiservice.dto.ReceiptOCRRequest;
import com.example.aiservice.dto.ReceiptOCRResponse;
import com.example.aiservice.service.ReceiptOCRService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/receipt-ocr")
@RequiredArgsConstructor
@Slf4j
public class ReceiptOCRController {

    private final ReceiptOCRService receiptOCRService;

    @PostMapping
    public ApiResponse<ReceiptOCRResponse> processReceiptImage(
            @Valid @RequestBody ReceiptOCRRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Received receipt OCR request: type={}, imageUrl={}",
                request.getReceiptType(),
                request.getImageUrl() != null ? "provided" : "base64");

        // Validate: phải có ít nhất imageUrl hoặc imageBase64
        if (!request.isValid()) {
            throw new IllegalArgumentException("Cần có imageUrl hoặc imageBase64");
        }

        try {
            ReceiptOCRResponse response = receiptOCRService.processReceiptImage(request);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("Error processing receipt image", e);
            throw e;
        }
    }
}
