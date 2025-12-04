package com.example.inventory_service.controller;

import com.example.inventory_service.common.ApiResponse;
import com.example.inventory_service.dto.StockByStoreDto;
import com.example.inventory_service.dto.CreateStockRequest;
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

    // 1) Tổng tồn kho tất cả sản phẩm (từ shop_stocks)
    @GetMapping
    public ApiResponse<List<StockByStoreDto>> getAllStock() {
        return ApiResponse.ok(stockService.getAllStockByStore());
    }

    // 2) Tồn kho của 1 sản phẩm tại tất cả các kho (từ shop_stocks)
    @GetMapping("/product/{productId}")
    public ApiResponse<List<StockByStoreDto>> getByProduct(@PathVariable Long productId) {
        return ApiResponse.ok(stockService.getStockByProductId(productId));
    }

    // 3) Tồn kho của 1 sản phẩm tại 1 kho cụ thể (từ shop_stocks)
    @GetMapping("/product/{productId}/store/{storeId}")
    public ApiResponse<StockByStoreDto> getByProductAndStore(
            @PathVariable Long productId,
            @PathVariable Long storeId) {
        return ApiResponse.ok(stockService.getStockByProductAndStore(productId, storeId));
    }

    // 4) Tồn kho của tất cả sản phẩm tại 1 kho (từ shop_stocks)
    @GetMapping("/store/{storeId}")
    public ApiResponse<List<StockByStoreDto>> getByStore(@PathVariable Long storeId) {
        return ApiResponse.ok(stockService.getStockByStoreId(storeId));
    }

    // 5) Tạo hoặc cập nhật tồn kho
    @PostMapping
    public ApiResponse<StockByStoreDto> createOrUpdateStock(@RequestBody CreateStockRequest request) {
        return ApiResponse.ok(stockService.createOrUpdateStock(request));
    }
}
