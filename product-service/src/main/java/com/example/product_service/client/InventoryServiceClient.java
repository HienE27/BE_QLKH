package com.example.product_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Client để gọi API sang Inventory-service
 */
@Component
public class InventoryServiceClient {

    private final RestTemplate restTemplate;

    @Value("${inventory.service.url:http://localhost:8082}")
    private String inventoryServiceUrl;

    public InventoryServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        System.out.println("🔧 InventoryServiceClient initialized with URL: " + inventoryServiceUrl);
    }

    /**
     * Xóa tất cả tồn kho của sản phẩm
     */
    public void deleteStockByProductId(Long productId) {
        String url = inventoryServiceUrl + "/api/stocks/product/" + productId;

        try {
            System.out.println("🔵 Calling Inventory-service: " + url);
            restTemplate.delete(url);
            System.out.println("✅ Successfully deleted stock for product: " + productId);
        } catch (Exception e) {
            System.err.println("❌ Failed to delete stock for product: " + productId + ", error: " + e.getMessage());
            // Log nhưng không throw để không chặn việc xóa sản phẩm
            // Nếu stock không tồn tại hoặc đã bị xóa, vẫn cho phép xóa sản phẩm
            e.printStackTrace();
        }
    }
}

