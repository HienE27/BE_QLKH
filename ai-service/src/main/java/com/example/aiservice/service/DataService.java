package com.example.aiservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataService {

    private final WebClient.Builder webClientBuilder;

    @Value("${api.gateway.url:http://api-gateway:8080}")
    private String apiGatewayUrl;

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /**
     * Lấy danh sách sản phẩm từ product-service
     */
    public String getProductsSummary(String token) {
        try {
            List<Map<String, Object>> products = fetchProductsList(token);

            if (products == null || products.isEmpty()) {
                return "Không có sản phẩm nào trong hệ thống.";
            }

            StringBuilder summary = new StringBuilder("Danh sách sản phẩm (tối đa 20 sản phẩm đầu tiên):\n");
            int count = 0;
            for (Map<String, Object> p : products) {
                if (count >= 20)
                    break;
                String code = String.valueOf(p.getOrDefault("code", "N/A"));
                String name = String.valueOf(p.getOrDefault("name", "N/A"));
                Object qty = p.getOrDefault("quantity", 0);
                Object price = p.getOrDefault("unitPrice", 0);
                summary.append(String.format("- %s (%s): Tồn kho %s, Giá %s\n",
                        code, name, qty, price));
                count++;
            }
            if (products.size() > 20) {
                summary.append(String.format("... và %d sản phẩm khác.\n", products.size() - 20));
            }
            return summary.toString();
        } catch (Exception e) {
            log.error("Error fetching products", e);
            return "Không thể lấy dữ liệu sản phẩm: " + e.getMessage();
        }
    }

    /**
     * Helper method để lấy products list từ API (xử lý cả ApiResponse và array)
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchProductsList(String token) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(apiGatewayUrl).build();
            Map<String, Object> response = webClient.get()
                    .uri("/api/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block(TIMEOUT);

            if (response != null && response.containsKey("data")) {
                // ApiResponse wrapper
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
                return data != null ? data : new ArrayList<>();
            } else {
                // Thử parse như array
                try {
                    List<Map<String, Object>> products = webClient.get()
                            .uri("/api/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                            })
                            .block(TIMEOUT);
                    return products != null ? products : new ArrayList<>();
                } catch (Exception e) {
                    log.warn("Failed to parse products as array", e);
                    return new ArrayList<>();
                }
            }
        } catch (Exception e) {
            log.error("Error fetching products list", e);
            return new ArrayList<>();
        }
    }

    /**
     * Lấy thống kê tồn kho từ inventory-service
     * Sử dụng tồn kho thực tế từ API stocks (tổng hợp từ tất cả các kho)
     */
    public String getInventorySummary(String token) {
        try {
            List<Map<String, Object>> products = fetchProductsList(token);
            // Lấy tồn kho thực tế từ API stocks
            Map<Long, Integer> stockMap = fetchStocksByProduct(token);

            if (products == null || products.isEmpty()) {
                return "Không có dữ liệu tồn kho.";
            }

            int totalProducts = products.size();
            int outOfStock = 0;
            int lowStock = 0;
            int inStock = 0;
            long totalQuantity = 0;
            double totalValue = 0;

            for (Map<String, Object> p : products) {
                // Lấy productId
                Object idObj = p.get("id");
                Long productId = null;
                if (idObj != null) {
                    try {
                        productId = Long.valueOf(idObj.toString());
                    } catch (NumberFormatException e) {
                        // Skip invalid productId
                    }
                }

                // Lấy tồn kho thực tế từ stockMap (tổng hợp từ tất cả các kho)
                int qty = (productId != null) ? stockMap.getOrDefault(productId, 0) : 0;
                Object priceObj = p.getOrDefault("unitPrice", 0);
                double price = priceObj instanceof Number ? ((Number) priceObj).doubleValue() : 0;

                totalQuantity += qty;
                totalValue += qty * price;

                if (qty == 0)
                    outOfStock++;
                else if (qty <= 10)
                    lowStock++;
                else
                    inStock++;
            }

            return String.format("""
                    Thống kê tồn kho:
                    - Tổng số sản phẩm: %d
                    - Hết hàng: %d
                    - Sắp hết (≤10): %d
                    - Còn hàng (>10): %d
                    - Tổng số lượng: %d
                    - Tổng giá trị: %.0f VNĐ
                    """, totalProducts, outOfStock, lowStock, inStock, totalQuantity, totalValue);
        } catch (Exception e) {
            log.error("Error fetching inventory", e);
            return "Không thể lấy dữ liệu tồn kho: " + e.getMessage();
        }
    }

    /**
     * Lấy thống kê đơn hàng từ order-service
     */
    public String getOrdersSummary(String token) {
        try {
            List<Map<String, Object>> orders = fetchOrdersList(token);

            if (orders == null || orders.isEmpty()) {
                return "Không có đơn hàng nào trong hệ thống.";
            }

            int totalOrders = orders.size();
            double totalRevenue = 0;
            for (Map<String, Object> order : orders) {
                Object amountObj = order.getOrDefault("totalAmount", 0);
                if (amountObj instanceof Number) {
                    totalRevenue += ((Number) amountObj).doubleValue();
                }
            }

            return String.format("""
                    Thống kê đơn hàng:
                    - Tổng số đơn hàng: %d
                    - Tổng doanh thu: %.0f VNĐ
                    """, totalOrders, totalRevenue);
        } catch (Exception e) {
            log.error("Error fetching orders", e);
            return "Không thể lấy dữ liệu đơn hàng: " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchOrdersList(String token) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(apiGatewayUrl).build();

            Map<String, Object> response = webClient.get()
                    .uri("/api/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block(TIMEOUT);

            if (response != null && response.containsKey("data")) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
                return data != null ? data : new ArrayList<>();
            } else {
                List<Map<String, Object>> orders = webClient.get()
                        .uri("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                        })
                        .block(TIMEOUT);
                return orders != null ? orders : new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Error fetching orders list", e);
            return new ArrayList<>();
        }
    }

    /**
     * Public method để lấy danh sách sản phẩm (cho các service khác sử dụng)
     */
    public List<Map<String, Object>> fetchProductsListPublic(String token) {
        return fetchProductsList(token);
    }

    /**
     * Lấy tồn kho từ tất cả các kho (tổng hợp theo productId)
     */
    public Map<Long, Integer> fetchStocksByProduct(String token) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(apiGatewayUrl).build();
            Map<String, Object> response = webClient.get()
                    .uri("/api/stocks")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block(TIMEOUT);

            Map<Long, Integer> stockMap = new HashMap<>();
            if (response != null && response.containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> stocks = (List<Map<String, Object>>) response.get("data");
                if (stocks != null) {
                    for (Map<String, Object> stock : stocks) {
                        Object productIdObj = stock.get("productId");
                        if (productIdObj != null) {
                            try {
                                Long productId = Long.valueOf(productIdObj.toString());
                                Integer quantity = getIntegerValue(stock, "quantity", 0);
                                stockMap.merge(productId, quantity, Integer::sum);
                            } catch (NumberFormatException e) {
                                log.warn("Invalid productId in stock: {}", productIdObj);
                            }
                        }
                    }
                }
            }
            return stockMap;
        } catch (Exception e) {
            log.error("Error fetching stocks", e);
            return new HashMap<>();
        }
    }

    private Integer getIntegerValue(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * Tìm sản phẩm theo từ khóa
     */
    public String searchProducts(String keyword, String token) {
        try {
            List<Map<String, Object>> products = fetchProductsList(token);

            if (products == null || products.isEmpty()) {
                return "Không tìm thấy sản phẩm nào.";
            }

            String keywordLower = keyword.toLowerCase();
            List<Map<String, Object>> matched = new ArrayList<>();
            for (Map<String, Object> p : products) {
                String code = String.valueOf(p.getOrDefault("code", "")).toLowerCase();
                String name = String.valueOf(p.getOrDefault("name", "")).toLowerCase();
                if (code.contains(keywordLower) || name.contains(keywordLower)) {
                    matched.add(p);
                }
            }

            if (matched.isEmpty()) {
                return String.format("Không tìm thấy sản phẩm nào chứa từ khóa '%s'.", keyword);
            }

            StringBuilder result = new StringBuilder(String.format("Tìm thấy %d sản phẩm:\n", matched.size()));
            for (Map<String, Object> p : matched) {
                String code = String.valueOf(p.getOrDefault("code", "N/A"));
                String name = String.valueOf(p.getOrDefault("name", "N/A"));
                Object qty = p.getOrDefault("quantity", 0);
                Object price = p.getOrDefault("unitPrice", 0);
                result.append(String.format("- %s (%s): Tồn kho %s, Giá %s\n", code, name, qty, price));
            }
            return result.toString();
        } catch (Exception e) {
            log.error("Error searching products", e);
            return "Không thể tìm kiếm sản phẩm: " + e.getMessage();
        }
    }
}
