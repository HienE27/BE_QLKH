package com.example.inventory_service.service;

import com.example.inventory_service.dto.StockDto;
import com.example.inventory_service.dto.StockByStoreDto;

import java.util.List;

public interface StockService {

    // ========= TỒN KHO TỪ shop_stocks (MỚI) =========
    
    // Tồn kho tất cả sản phẩm tại tất cả các kho
    List<StockByStoreDto> getAllStockByStore();

    // Tồn kho tất cả sản phẩm tại tất cả các kho (với pagination)
    org.springframework.data.domain.Page<StockByStoreDto> getAllStockByStore(org.springframework.data.domain.Pageable pageable);

    // Tồn kho của 1 sản phẩm tại tất cả các kho
    List<StockByStoreDto> getStockByProductId(Long productId);

    // Tồn kho của 1 sản phẩm tại 1 kho cụ thể
    StockByStoreDto getStockByProductAndStore(Long productId, Long storeId);

    // Tồn kho của tất cả sản phẩm tại 1 kho
    List<StockByStoreDto> getStockByStoreId(Long storeId);

    // ========= TỒN KHO TỪ LỊCH SỬ (CŨ - GIỮ LẠI ĐỂ TƯƠNG THÍCH) =========
    
    // tồn kho tất cả sản phẩm (từ lịch sử import/export)
    List<StockDto> getAllStock();

    // tồn kho theo 1 productId (từ lịch sử import/export)
    StockDto getStockByProduct(Long productId);

    // Kiểm tra tồn kho có đủ để xuất không
    boolean hasEnoughStock(Long productId, int quantity);

    // Lấy số lượng tồn hiện tại
    int getCurrentStock(Long productId);

    // Tạo hoặc cập nhật tồn kho
    StockByStoreDto createOrUpdateStock(com.example.inventory_service.dto.CreateStockRequest request);
    
    // Xóa tất cả tồn kho của 1 sản phẩm
    void deleteByProductId(Long productId);
}
