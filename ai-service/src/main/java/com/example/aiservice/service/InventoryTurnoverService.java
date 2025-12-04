package com.example.aiservice.service;

import com.example.aiservice.dto.InventoryTurnoverResponse;
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
public class InventoryTurnoverService {

    private final WebClient.Builder webClientBuilder;
    private final GeminiService geminiService;

    @Value("${api.gateway.url:http://api-gateway:8080}")
    private String apiGatewayUrl;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * Đánh giá chu kỳ tồn kho (Inventory Turnover)
     */
    public InventoryTurnoverResponse analyzeInventoryTurnover(String token, int periodDays) {
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(periodDays);

            List<Map<String, Object>> stocks = fetchStocks(token);
            List<Map<String, Object>> products = fetchProducts(token);
            List<Map<String, Object>> exports = fetchExports(token, startDate, endDate);
            List<Map<String, Object>> imports = fetchImports(token, startDate, endDate);

            // Tạo map productId -> product info
            Map<Long, Map<String, Object>> productMap = products.stream()
                    .collect(Collectors.toMap(
                            p -> Long.valueOf(p.get("id").toString()),
                            p -> p,
                            (a, b) -> a));

            // Tính tổng giá trị tồn kho
            double totalInventoryValue = calculateTotalInventoryValue(stocks, productMap);

            // Tính tổng giá trị bán ra
            double totalSalesValue = calculateTotalSalesValue(exports);

            // Tỉ lệ vòng quay tổng thể
            // Công thức: Tổng giá trị bán ra / Tổng giá trị tồn kho hiện tại
            // Nếu không có tồn kho, nhưng có bán hàng, thì tỉ lệ = vô cùng (không tính
            // được)
            // Nếu có tồn kho nhưng không bán, thì tỉ lệ = 0
            double overallTurnoverRate = 0.0;
            if (totalInventoryValue > 0) {
                overallTurnoverRate = totalSalesValue / totalInventoryValue;
            }
            // Nếu totalInventoryValue = 0 nhưng có bán hàng, không tính được tỉ lệ (giữ
            // nguyên 0.0)

            // Phân tích từng sản phẩm
            List<InventoryTurnoverResponse.ProductTurnover> productTurnovers = new ArrayList<>();
            List<InventoryTurnoverResponse.DeadStock> deadStocks = new ArrayList<>();
            List<InventoryTurnoverResponse.OverstockedItem> overstockedItems = new ArrayList<>();

            for (Map<String, Object> stock : stocks) {
                Long productId = Long.valueOf(stock.get("productId").toString());
                Map<String, Object> product = productMap.get(productId);
                if (product == null)
                    continue;

                Integer currentStock = getIntegerValue(stock, "quantity", 0);
                Double unitPrice = getDoubleValue(product, "unitPrice", 0.0);
                double inventoryValue = currentStock * unitPrice;

                // Tính số lượng bán trong kỳ
                int quantitySold = calculateQuantitySold(exports, productId);
                double salesValue = quantitySold * unitPrice;

                // Tính số ngày tồn kho trung bình
                int daysInStock = calculateDaysInStock(imports, exports, productId, periodDays);

                // Tỉ lệ vòng quay
                double turnoverRate = inventoryValue > 0 ? salesValue / inventoryValue : 0.0;

                // Phân loại hiệu quả
                String efficiency = classifyEfficiency(turnoverRate, daysInStock);

                InventoryTurnoverResponse.ProductTurnover pt = new InventoryTurnoverResponse.ProductTurnover();
                pt.setProductId(productId);
                pt.setProductCode(String.valueOf(product.getOrDefault("code", "")));
                pt.setProductName(String.valueOf(product.getOrDefault("name", "")));
                pt.setTurnoverRate(turnoverRate);
                pt.setDaysInStock(daysInStock);
                pt.setEfficiency(efficiency);
                productTurnovers.add(pt);

                // Hàng tồn kho lâu (slow-moving stock) - không bán trong 60 ngày và có tồn kho
                if (currentStock > 0 && quantitySold == 0) {
                    int daysSinceLastSale = calculateDaysSinceLastSale(exports, productId, startDate);
                    if (daysSinceLastSale >= 60) {
                        InventoryTurnoverResponse.DeadStock deadStock = new InventoryTurnoverResponse.DeadStock();
                        deadStock.setProductId(productId);
                        deadStock.setProductCode(String.valueOf(product.getOrDefault("code", "")));
                        deadStock.setProductName(String.valueOf(product.getOrDefault("name", "")));
                        deadStock.setQuantity(currentStock);
                        deadStock.setDaysSinceLastSale(daysSinceLastSale);
                        deadStock.setTotalValue(inventoryValue);
                        deadStock.setRecommendation("Xem xét giảm giá mạnh hoặc ngừng nhập hàng");
                        deadStocks.add(deadStock);
                    }
                }

                // Hàng tồn quá lâu - tồn kho > 90 ngày
                if (daysInStock > 90 && currentStock > 0) {
                    int optimalStock = (int) Math.ceil(quantitySold * 0.3); // 30% của lượng bán
                    if (currentStock > optimalStock * 2) {
                        InventoryTurnoverResponse.OverstockedItem overstocked = new InventoryTurnoverResponse.OverstockedItem();
                        overstocked.setProductId(productId);
                        overstocked.setProductCode(String.valueOf(product.getOrDefault("code", "")));
                        overstocked.setProductName(String.valueOf(product.getOrDefault("name", "")));
                        overstocked.setCurrentStock(currentStock);
                        overstocked.setOptimalStock(optimalStock);
                        overstocked.setExcessQuantity(currentStock - optimalStock);
                        overstocked.setRecommendation("Xem xét giảm giá để giải phóng tồn kho");
                        overstockedItems.add(overstocked);
                    }
                }
            }

            // Tạo phân tích và đề xuất bằng AI
            String analysis = generateTurnoverAnalysis(overallTurnoverRate, productTurnovers, deadStocks,
                    overstockedItems);
            List<String> recommendations = generateRecommendations(deadStocks, overstockedItems);

            return new InventoryTurnoverResponse(
                    overallTurnoverRate,
                    productTurnovers,
                    deadStocks,
                    overstockedItems,
                    analysis,
                    recommendations);

        } catch (Exception e) {
            log.error("Error analyzing inventory turnover", e);
            throw new RuntimeException("Không thể đánh giá chu kỳ tồn kho: " + e.getMessage());
        }
    }

    private double calculateTotalInventoryValue(List<Map<String, Object>> stocks,
            Map<Long, Map<String, Object>> productMap) {
        double total = 0.0;
        for (Map<String, Object> stock : stocks) {
            Long productId = Long.valueOf(stock.get("productId").toString());
            Map<String, Object> product = productMap.get(productId);
            if (product != null) {
                Integer quantity = getIntegerValue(stock, "quantity", 0);
                Double unitPrice = getDoubleValue(product, "unitPrice", 0.0);
                total += quantity * unitPrice;
            }
        }
        return total;
    }

    private double calculateTotalSalesValue(List<Map<String, Object>> exports) {
        double total = 0.0;
        for (Map<String, Object> export : exports) {
            // Chỉ tính các phiếu xuất đã được xác nhận (status = "EXPORTED")
            Object status = export.get("status");
            if (status == null || !"EXPORTED".equals(status.toString())) {
                continue;
            }
            Double totalAmount = getDoubleValue(export, "totalAmount", 0.0);
            total += totalAmount;
        }
        return total;
    }

    private int calculateQuantitySold(List<Map<String, Object>> exports, Long productId) {
        int total = 0;
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
                            total += getIntegerValue(itemMap, "quantity", 0);
                        }
                    }
                }
            }
        }
        return total;
    }

    private int calculateDaysInStock(List<Map<String, Object>> imports, List<Map<String, Object>> exports,
            Long productId, int periodDays) {
        // Đơn giản: tính số ngày từ lần nhập gần nhất
        LocalDate lastImportDate = null;
        for (Map<String, Object> importOrder : imports) {
            Object items = importOrder.get("items");
            if (items instanceof List) {
                for (Object item : (List<?>) items) {
                    if (item instanceof Map) {
                        Map<String, Object> itemMap = (Map<String, Object>) item;
                        if (Objects.equals(getLongValue(itemMap, "productId"), productId)) {
                            Object importDateObj = importOrder.get("importDate");
                            if (importDateObj != null) {
                                LocalDate importDate = LocalDate.parse(importDateObj.toString());
                                if (lastImportDate == null || importDate.isAfter(lastImportDate)) {
                                    lastImportDate = importDate;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (lastImportDate != null) {
            return (int) java.time.temporal.ChronoUnit.DAYS.between(lastImportDate, LocalDate.now());
        }
        return periodDays; // Nếu không có lịch sử nhập, giả định tồn từ đầu kỳ
    }

    private int calculateDaysSinceLastSale(List<Map<String, Object>> exports, Long productId, LocalDate referenceDate) {
        // Ngày tham chiếu: 20 tháng 11 (năm hiện tại hoặc năm trước nếu đã qua)
        LocalDate baseDate = LocalDate.of(LocalDate.now().getYear(), 11, 20);
        if (baseDate.isAfter(LocalDate.now())) {
            // Nếu ngày 20/11 chưa đến trong năm này, dùng năm trước
            baseDate = baseDate.minusYears(1);
        }

        LocalDate lastSaleDate = null;
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
                            Object exportDateObj = export.get("exportDate");
                            if (exportDateObj != null) {
                                LocalDate exportDate = LocalDate.parse(exportDateObj.toString());
                                // Chỉ tính các lần bán sau ngày 20/11
                                if (exportDate.isAfter(baseDate) || exportDate.isEqual(baseDate)) {
                                    if (lastSaleDate == null || exportDate.isAfter(lastSaleDate)) {
                                        lastSaleDate = exportDate;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (lastSaleDate != null) {
            return (int) java.time.temporal.ChronoUnit.DAYS.between(lastSaleDate, LocalDate.now());
        }
        // Nếu không có bán sau ngày 20/11, tính từ ngày 20/11
        return (int) java.time.temporal.ChronoUnit.DAYS.between(baseDate, LocalDate.now());
    }

    private String classifyEfficiency(double turnoverRate, int daysInStock) {
        if (turnoverRate >= 2.0 || daysInStock <= 30) {
            return "HIGH";
        } else if (turnoverRate >= 1.0 || daysInStock <= 60) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private String generateTurnoverAnalysis(
            double overallRate,
            List<InventoryTurnoverResponse.ProductTurnover> turnovers,
            List<InventoryTurnoverResponse.DeadStock> deadStocks,
            List<InventoryTurnoverResponse.OverstockedItem> overstocked) {
        try {
            StringBuilder context = new StringBuilder("Phân tích chu kỳ tồn kho:\n");
            context.append(String.format("- Tỉ lệ vòng quay tổng thể: %.2f\n", overallRate));
            context.append(String.format("- Số sản phẩm hiệu quả cao: %d\n",
                    turnovers.stream().filter(t -> "HIGH".equals(t.getEfficiency())).count()));
            context.append(String.format("- Hàng tồn kho lâu: %d sản phẩm\n", deadStocks.size()));
            context.append(String.format("- Hàng tồn quá lâu: %d sản phẩm\n", overstocked.size()));

            String prompt = "Bạn là chuyên gia quản lý kho. " +
                    "Hãy phân tích dữ liệu chu kỳ tồn kho sau và đưa ra nhận định (2-3 câu):\n\n" +
                    context.toString();

            return geminiService.invokeGemini(prompt);
        } catch (Exception e) {
            log.warn("Failed to generate AI analysis", e);
            return String.format(
                    "Tỉ lệ vòng quay tổng thể: %.2f. %d sản phẩm hiệu quả cao, %d hàng chết, %d hàng tồn quá lâu.",
                    overallRate,
                    turnovers.stream().filter(t -> "HIGH".equals(t.getEfficiency())).count(),
                    deadStocks.size(),
                    overstocked.size());
        }
    }

    private List<String> generateRecommendations(
            List<InventoryTurnoverResponse.DeadStock> deadStocks,
            List<InventoryTurnoverResponse.OverstockedItem> overstocked) {
        List<String> recommendations = new ArrayList<>();

        if (!deadStocks.isEmpty()) {
            recommendations.add(String.format(
                    "Có %d sản phẩm hàng tồn kho lâu. Đề xuất: Giảm giá mạnh (30-50%%) hoặc ngừng nhập hàng.",
                    deadStocks.size()));
        }

        if (!overstocked.isEmpty()) {
            recommendations.add(String.format(
                    "Có %d sản phẩm tồn quá lâu. Đề xuất: Giảm giá để giải phóng tồn kho và tăng vòng quay.",
                    overstocked.size()));
        }

        if (deadStocks.isEmpty() && overstocked.isEmpty()) {
            recommendations.add("Tồn kho đang được quản lý hiệu quả. Tiếp tục duy trì.");
        }

        return recommendations;
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

    private Double getDoubleValue(Map<String, Object> map, String key, Double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
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
