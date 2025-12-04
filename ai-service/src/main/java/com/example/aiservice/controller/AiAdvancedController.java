package com.example.aiservice.controller;

import com.example.aiservice.common.ApiResponse;
import com.example.aiservice.dto.*;
import com.example.aiservice.service.AiAnalysisService;
import com.example.aiservice.service.DataService;
import com.example.aiservice.service.GeminiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiAdvancedController {

    private final GeminiService geminiService;
    private final DataService dataService;
    private final AiAnalysisService analysisService;
    private final ObjectMapper objectMapper;

    /**
     * 1. Cảnh báo thông minh cho Dashboard
     */
    @GetMapping("/dashboard-alerts")
    public ApiResponse<DashboardAlertsResponse> getDashboardAlerts(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Fetching dashboard alerts");
        String token = extractToken(authHeader);
        
        try {
            DashboardAlertsResponse alerts = analysisService.generateDashboardAlerts(token);
            return ApiResponse.ok(alerts);
        } catch (Exception e) {
            log.error("Error generating dashboard alerts", e);
            throw e;
        }
    }

    /**
     * 2. Phân tích ABC tồn kho
     */
    @GetMapping("/abc-analysis")
    public ApiResponse<ABCAnalysisResponse> getABCAnalysis(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Generating ABC analysis");
        String token = extractToken(authHeader);
        
        try {
            ABCAnalysisResponse analysis = analysisService.generateABCAnalysis(token);
            return ApiResponse.ok(analysis);
        } catch (Exception e) {
            log.error("Error generating ABC analysis", e);
            throw e;
        }
    }

    /**
     * 3. Gợi ý giá bán tối ưu
     */
    @PostMapping("/price-suggestion")
    public ApiResponse<PriceSuggestionResponse> getPriceSuggestion(
            @Valid @RequestBody PriceSuggestionRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Generating price suggestion for: {}", request.getProductName());
        
        try {
            PriceSuggestionResponse suggestion = analysisService.generatePriceSuggestion(request);
            return ApiResponse.ok(suggestion);
        } catch (Exception e) {
            log.error("Error generating price suggestion", e);
            throw e;
        }
    }

    /**
     * 4. Phân tích xu hướng bán hàng
     */
    @GetMapping("/sales-trend")
    public ApiResponse<SalesTrendResponse> getSalesTrend(
            @RequestParam(defaultValue = "MONTHLY") String period,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Generating sales trend analysis for period: {}", period);
        String token = extractToken(authHeader);
        
        try {
            SalesTrendResponse trend = analysisService.generateSalesTrend(token, period);
            return ApiResponse.ok(trend);
        } catch (Exception e) {
            log.error("Error generating sales trend", e);
            throw e;
        }
    }

    /**
     * 5. Tự động tạo báo cáo
     */
    @PostMapping("/generate-report")
    public ApiResponse<ReportResponse> generateReport(
            @Valid @RequestBody ReportRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Generating report: {} for period: {}", request.getReportType(), request.getPeriod());
        String token = extractToken(authHeader);
        
        try {
            ReportResponse report = analysisService.generateReport(token, request);
            return ApiResponse.ok(report);
        } catch (Exception e) {
            log.error("Error generating report", e);
            throw e;
        }
    }

    /**
     * 6. Gợi ý combo sản phẩm
     */
    @GetMapping("/combo-suggestions")
    public ApiResponse<ComboSuggestionResponse> getComboSuggestions(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Generating combo suggestions");
        String token = extractToken(authHeader);
        
        try {
            ComboSuggestionResponse combos = analysisService.generateComboSuggestions(token);
            return ApiResponse.ok(combos);
        } catch (Exception e) {
            log.error("Error generating combo suggestions", e);
            throw e;
        }
    }

    /**
     * 7. Nhận diện hóa đơn/phiếu nhập từ ảnh (OCR)
     */
    @PostMapping("/ocr-document")
    public ApiResponse<ImageOCRResponse> ocrDocument(
            @Valid @RequestBody ImageOCRRequest request) {
        log.info("Processing OCR for document type: {}", request.getDocumentType());
        
        try {
            ImageOCRResponse result = analysisService.processOCR(request);
            return ApiResponse.ok(result);
        } catch (Exception e) {
            log.error("Error processing OCR", e);
            throw e;
        }
    }

    private String extractToken(String authHeader) {
        return authHeader != null && authHeader.startsWith("Bearer ") 
            ? authHeader.substring(7) 
            : null;
    }
}

