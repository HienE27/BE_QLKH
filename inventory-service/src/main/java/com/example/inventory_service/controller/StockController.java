package com.example.inventory_service.controller;

import com.example.inventory_service.common.ApiResponse;
import com.example.inventory_service.dto.StockDto;
import com.example.inventory_service.service.StockService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    // 1) Tổng tồn kho tất cả sản phẩm
    @GetMapping
    public ApiResponse<List<StockDto>> getAllStock() {
        return ApiResponse.ok(stockService.getAllStock());
    }

    // 2) Tồn kho theo 1 productId
    @GetMapping("/{productId}")
    public ApiResponse<StockDto> getByProduct(@PathVariable Long productId) {
        return ApiResponse.ok(stockService.getStockByProduct(productId));
    }
}
