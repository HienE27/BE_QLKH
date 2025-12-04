package com.example.aiservice.service;

import com.example.aiservice.dto.StockOptimizationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockOptimizationService {

    private final WebClient.Builder webClientBuilder;
    private final GeminiService geminiService;

    @Value("${api.gateway.url:http://api-gateway:8080}")
    private String apiGatewayUrl;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * Tự động gợi ý cấu trúc kho tối ưu
     */
    public StockOptimizationResponse optimizeStock(String token) {
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(90);

            List<Map<String, Object>> stocks = fetchStocks(token);
            List<Map<String, Object>> products = fetchProducts(token);
            List<Map<String, Object>> stores = fetchStores(token);
            List<Map<String, Object>> exports = fetchExports(token, startDate, endDate);
            List<Map<String, Object>> imports = fetchImports(token, startDate, endDate);

            // Tạo maps
            Map<Long, Map<String, Object>> productMap = products.stream()
                    .collect(Collectors.toMap(
                            p -> Long.valueOf(p.get("id").toString()),
                            p -> p,
                            (a, b) -> a));

            Map<Long, Map<String, Object>> storeMap = stores.stream()
                    .collect(Collectors.toMap(
                            s -> Long.valueOf(s.get("id").toString()),
                            s -> s,
                            (a, b) -> a));

            // Phân tích và tối ưu từng sản phẩm
            List<StockOptimizationResponse.ProductOptimization> optimizations = new ArrayList<>();
            List<StockOptimizationResponse.WarehouseRecommendation> warehouseRecommendations = new ArrayList<>();

            for (Map<String, Object> stock : stocks) {
                Long productId = Long.valueOf(stock.get("productId").toString());
                Map<String, Object> product = productMap.get(productId);
                if (product == null)
                    continue;

                Integer currentStock = getIntegerValue(stock, "quantity", 0);
                Long storeId = getLongValue(stock, "storeId");

                // Tính tốc độ bán trung bình
                Double avgDailySales = calculateAverageDailySales(exports, productId);

                // Tính mức tồn min/max tối ưu
                int minStock = calculateMinStock(avgDailySales);
                int maxStock = calculateMaxStock(avgDailySales);
                int optimalReorderQuantity = calculateOptimalReorderQuantity(avgDailySales, minStock, maxStock);

                StockOptimizationResponse.ProductOptimization opt = new StockOptimizationResponse.ProductOptimization();
                opt.setProductId(productId);
                opt.setProductCode(String.valueOf(product.getOrDefault("code", "")));
                opt.setProductName(String.valueOf(product.getOrDefault("name", "")));
                opt.setCurrentStock(currentStock);
                opt.setMinStock(minStock);
                opt.setMaxStock(maxStock);
                opt.setOptimalReorderQuantity(optimalReorderQuantity);
                opt.setReasoning(String.format(
                        "Dựa trên tốc độ bán %.1f/ngày. Mức tồn tối thiểu: %d (7 ngày), tối đa: %d (21 ngày).",
                        avgDailySales, minStock, maxStock));
                optimizations.add(opt);

                // Gợi ý kho hàng dựa trên lịch sử xuất
                Long recommendedStoreId = recommendWarehouse(stocks, exports, productId, storeMap);
                if (recommendedStoreId != null && !recommendedStoreId.equals(storeId)) {
                    Map<String, Object> recommendedStore = storeMap.get(recommendedStoreId);
                    if (recommendedStore != null) {
                        StockOptimizationResponse.WarehouseRecommendation wr = new StockOptimizationResponse.WarehouseRecommendation();
                        wr.setProductId(productId);
                        wr.setProductCode(String.valueOf(product.getOrDefault("code", "")));
                        wr.setProductName(String.valueOf(product.getOrDefault("name", "")));
                        wr.setRecommendedStoreId(recommendedStoreId);
                        wr.setRecommendedStoreName(String.valueOf(recommendedStore.getOrDefault("name", "")));
                        wr.setReasoning("Kho này có lịch sử xuất hàng tốt hơn cho sản phẩm này");
                        warehouseRecommendations.add(wr);
                    }
                }
            }

            // Phân tích danh mục
            List<StockOptimizationResponse.CategoryOptimization> categoryOptimizations = analyzeCategories(products,
                    exports);

            // Tạo summary
            String summary = generateOptimizationSummary(optimizations, warehouseRecommendations);

            return new StockOptimizationResponse(
                    optimizations,
                    warehouseRecommendations,
                    categoryOptimizations,
                    summary);

        } catch (Exception e) {
            log.error("Error optimizing stock", e);
            throw new RuntimeException("Không thể tối ưu cấu trúc kho: " + e.getMessage());
        }
    }

    private int calculateMinStock(Double avgDailySales) {
        if (avgDailySales == null || avgDailySales <= 0) {
            return 10; // Default
        }
        // Mức tồn tối thiểu = 7 ngày bán
        return (int) Math.ceil(avgDailySales * 7);
    }

    private int calculateMaxStock(Double avgDailySales) {
        if (avgDailySales == null || avgDailySales <= 0) {
            return 100; // Default
        }
        // Mức tồn tối đa = 21 ngày bán
        return (int) Math.ceil(avgDailySales * 21);
    }

    private int calculateOptimalReorderQuantity(Double avgDailySales, int minStock, int maxStock) {
        if (avgDailySales == null || avgDailySales <= 0) {
            return 50; // Default
        }
        // Số lượng nhập lại tối ưu = 14 ngày bán
        return (int) Math.ceil(avgDailySales * 14);
    }

    private Long recommendWarehouse(
            List<Map<String, Object>> stocks,
            List<Map<String, Object>> exports,
            Long productId,
            Map<Long, Map<String, Object>> storeMap) {
        // Tìm kho có nhiều xuất nhất cho sản phẩm này
        Map<Long, Integer> exportCountByStore = new HashMap<>();

        for (Map<String, Object> export : exports) {
            // Chỉ tính các phiếu xuất đã được xác nhận (status = "EXPORTED")
            Object status = export.get("status");
            if (status == null || !"EXPORTED".equals(status.toString())) {
                continue;
            }
            Object items = export.get("items");
            if (items instanceof List) {
                for (Object item : (List<?>) items) {
                    if (item instanceof Map) {
                        Map<String, Object> itemMap = (Map<String, Object>) item;
                        if (Objects.equals(getLongValue(itemMap, "productId"), productId)) {
                            Long itemStoreId = getLongValue(itemMap, "storeId");
                            if (itemStoreId != null) {
                                exportCountByStore.merge(itemStoreId, 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }

        // Tìm kho có nhiều xuất nhất
        return exportCountByStore.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private List<StockOptimizationResponse.CategoryOptimization> analyzeCategories(
            List<Map<String, Object>> products, List<Map<String, Object>> exports) {
        // Phân tích đơn giản theo category (nếu có)
        // Có thể mở rộng để phân tích chi tiết hơn
        List<StockOptimizationResponse.CategoryOptimization> categoryOpts = new ArrayList<>();

        StockOptimizationResponse.CategoryOptimization catOpt = new StockOptimizationResponse.CategoryOptimization();
        catOpt.setCategoryName("Tất cả danh mục");
        catOpt.setRecommendations(Arrays.asList(
                "Duy trì mức tồn kho tối ưu cho từng sản phẩm",
                "Theo dõi tốc độ bán để điều chỉnh mức tồn",
                "Tối ưu vị trí kho để giảm thời gian vận chuyển"));
        catOpt.setAnalysis("Phân tích tổng thể cho thấy cần tối ưu mức tồn kho dựa trên tốc độ bán thực tế.");
        categoryOpts.add(catOpt);

        return categoryOpts;
    }

    private String generateOptimizationSummary(
            List<StockOptimizationResponse.ProductOptimization> optimizations,
            List<StockOptimizationResponse.WarehouseRecommendation> warehouseRecs) {
        return String.format(
                "Đã phân tích %d sản phẩm và đưa ra %d gợi ý tối ưu kho hàng. " +
                        "Hệ thống đề xuất điều chỉnh mức tồn min/max và vị trí kho để tối ưu hiệu quả.",
                optimizations.size(), warehouseRecs.size());
    }

    private Double calculateAverageDailySales(List<Map<String, Object>> exports, Long productId) {
        int totalSold = 0;
        Set<String> dates = new HashSet<>();

        for (Map<String, Object> export : exports) {
            // Chỉ tính các phiếu xuất đã được xác nhận (status = "EXPORTED")
            Object status = export.get("status");
            if (status == null || !"EXPORTED".equals(status.toString())) {
                continue;
            }

            Object exportDate = export.get("exportDate");
            if (exportDate != null) {
                dates.add(exportDate.toString());
            }

            Object items = export.get("items");
            if (items instanceof List) {
                for (Object item : (List<?>) items) {
                    if (item instanceof Map) {
                        Map<String, Object> itemMap = (Map<String, Object>) item;
                        if (Objects.equals(getLongValue(itemMap, "productId"), productId)) {
                            totalSold += getIntegerValue(itemMap, "quantity", 0);
                        }
                    }
                }
            }
        }

        int days = Math.max(1, dates.size());
        return totalSold / (double) days;
    }

    private List<Map<String, Object>> fetchStocks(String token) {
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

            if (response != null && response.containsKey("data")) {
                return (List<Map<String, Object>>) response.get("data");
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Error fetching stocks", e);
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> fetchProducts(String token) {
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
                return (List<Map<String, Object>>) response.get("data");
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Error fetching products", e);
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> fetchStores(String token) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(apiGatewayUrl).build();
            Map<String, Object> response = webClient.get()
                    .uri("/api/stores")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block(TIMEOUT);

            if (response != null && response.containsKey("data")) {
                return (List<Map<String, Object>>) response.get("data");
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Error fetching stores", e);
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> fetchExports(String token, LocalDate from, LocalDate to) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(apiGatewayUrl).build();
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/exports")
                            .queryParam("from", from.toString())
                            .queryParam("to", to.toString())
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block(TIMEOUT);

            if (response != null && response.containsKey("data")) {
                return (List<Map<String, Object>>) response.get("data");
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Error fetching exports", e);
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> fetchImports(String token, LocalDate from, LocalDate to) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(apiGatewayUrl).build();
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/imports")
                            .queryParam("from", from.toString())
                            .queryParam("to", to.toString())
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block(TIMEOUT);

            if (response != null && response.containsKey("data")) {
                return (List<Map<String, Object>>) response.get("data");
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Error fetching imports", e);
            return new ArrayList<>();
        }
    }

    private Integer getIntegerValue(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }
}
