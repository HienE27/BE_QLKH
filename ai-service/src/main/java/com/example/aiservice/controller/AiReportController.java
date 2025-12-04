package com.example.aiservice.controller;

import com.example.aiservice.common.ApiResponse;
import com.example.aiservice.dto.*;
import com.example.aiservice.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RequestMapping("/api/ai/reports")
@RequiredArgsConstructor
@Slf4j
public class AiReportController {

    private final SmartInventoryAlertService smartInventoryAlertService;
    private final DemandForecastingService demandForecastingService;
    private final SalesInsightService salesInsightService;
    private final InventoryTurnoverService inventoryTurnoverService;
    private final StockOptimizationService stockOptimizationService;

    /**
     * 1. Cảnh báo tồn kho thông minh
     */
    @GetMapping("/inventory-alerts")
    public ApiResponse<SmartInventoryAlertResponse> getInventoryAlerts(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Getting smart inventory alerts");
        try {
            String token = extractToken(authHeader);
            SmartInventoryAlertResponse response = smartInventoryAlertService.analyzeInventoryAlerts(token);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("Error getting inventory alerts", e);
            throw new RuntimeException("Không thể lấy cảnh báo tồn kho: " + e.getMessage());
        }
    }

    /**
     * 2. Dự đoán nhu cầu nhập hàng
     */
    @GetMapping("/demand-forecast")
    public ApiResponse<DemandForecastResponse> getDemandForecast(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Getting demand forecast");
        try {
            String token = extractToken(authHeader);
            DemandForecastResponse response = demandForecastingService.forecastDemand(token);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("Error getting demand forecast", e);
            throw new RuntimeException("Không thể dự đoán nhu cầu nhập hàng: " + e.getMessage());
        }
    }

    /**
     * 2.1. Dự đoán nhu cầu cho một sản phẩm cụ thể
     */
    @GetMapping("/demand-forecast/product/{productId}")
    public ApiResponse<ProductDemandForecastResponse> getProductDemandForecast(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "30") Integer days,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Getting demand forecast for product {} for {} days", productId, days);
        try {
            String token = extractToken(authHeader);
            ProductDemandForecastResponse response = demandForecastingService.forecastProductDemand(token, productId,
                    days);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("Error getting product demand forecast", e);
            throw new RuntimeException("Không thể dự đoán nhu cầu cho sản phẩm: " + e.getMessage());
        }
    }

    /**
     * 3. Phân tích lịch sử bán hàng
     */
    @GetMapping("/sales-insights")
    public ApiResponse<SalesInsightResponse> getSalesInsights(
            @RequestParam(defaultValue = "30") int days,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Getting sales insights for {} days", days);
        try {
            String token = extractToken(authHeader);
            SalesInsightResponse response = salesInsightService.analyzeSalesInsights(token, days);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("Error getting sales insights", e);
            throw new RuntimeException("Không thể phân tích lịch sử bán hàng: " + e.getMessage());
        }
    }

    /**
     * 4. Đánh giá chu kỳ tồn kho
     */
    @GetMapping("/inventory-turnover")
    public ApiResponse<InventoryTurnoverResponse> getInventoryTurnover(
            @RequestParam(defaultValue = "90") int periodDays,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Getting inventory turnover for {} days", periodDays);
        try {
            String token = extractToken(authHeader);
            InventoryTurnoverResponse response = inventoryTurnoverService.analyzeInventoryTurnover(token, periodDays);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("Error getting inventory turnover", e);
            throw new RuntimeException("Không thể đánh giá chu kỳ tồn kho: " + e.getMessage());
        }
    }

    /**
     * 5. Tự động gợi ý cấu trúc kho
     */
    @GetMapping("/stock-optimization")
    public ApiResponse<StockOptimizationResponse> getStockOptimization(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Getting stock optimization");
        try {
            String token = extractToken(authHeader);
            StockOptimizationResponse response = stockOptimizationService.optimizeStock(token);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("Error getting stock optimization", e);
            throw new RuntimeException("Không thể tối ưu cấu trúc kho: " + e.getMessage());
        }
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader;
    }
}
