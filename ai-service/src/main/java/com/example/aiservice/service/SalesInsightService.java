package com.example.aiservice.service;

import com.example.aiservice.dto.SalesInsightResponse;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesInsightService {

    private final WebClient.Builder webClientBuilder;
    private final GeminiService geminiService;

    @Value("${api.gateway.url:http://api-gateway:8080}")
    private String apiGatewayUrl;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * Phân tích lịch sử bán hàng để đưa ra insights
     */
    public SalesInsightResponse analyzeSalesInsights(String token, int days) {
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(days);

            List<Map<String, Object>> exports = fetchExports(token, startDate, endDate);
            List<Map<String, Object>> products = fetchProducts(token);

            // Tạo map productId -> product info
            Map<Long, Map<String, Object>> productMap = products.stream()
                    .collect(Collectors.toMap(
                            p -> Long.valueOf(p.get("id").toString()),
                            p -> p,
                            (a, b) -> a));

            // Phân tích doanh thu
            SalesInsightResponse.RevenueAnalysis revenueAnalysis = analyzeRevenue(exports, days);

            // Top sản phẩm bán chạy
            List<SalesInsightResponse.TopProduct> topProducts = calculateTopProducts(exports, productMap);

            // Sản phẩm giảm doanh số
            List<SalesInsightResponse.DecliningProduct> decliningProducts = findDecliningProducts(exports, productMap,
                    days);

            // Giờ bán tốt nhất
            SalesInsightResponse.BestSellingHours bestSellingHours = analyzeBestSellingHours(exports);

            // Sản phẩm theo mùa vụ
            List<SalesInsightResponse.SeasonalProduct> seasonalProducts = analyzeSeasonalProducts(exports, productMap);

            // Tạo phân tích tổng quan bằng AI
            String overallAnalysis = generateOverallAnalysis(revenueAnalysis, topProducts, decliningProducts, days);

            return new SalesInsightResponse(
                    revenueAnalysis,
                    topProducts,
                    decliningProducts,
                    bestSellingHours,
                    seasonalProducts,
                    overallAnalysis);

        } catch (Exception e) {
            log.error("Error analyzing sales insights", e);
            throw new RuntimeException("Không thể phân tích lịch sử bán hàng: " + e.getMessage());
        }
    }

    private SalesInsightResponse.RevenueAnalysis analyzeRevenue(List<Map<String, Object>> exports, int days) {
        // Tính doanh thu hiện tại (nửa sau của period)
        int midPoint = days / 2;
        LocalDate midDate = LocalDate.now().minusDays(midPoint);

        double currentRevenue = 0;
        double previousRevenue = 0;

        for (Map<String, Object> export : exports) {
            // Chỉ tính các phiếu xuất đã được xác nhận (status = "EXPORTED")
            Object status = export.get("status");
            if (status == null || !"EXPORTED".equals(status.toString())) {
                continue;
            }

            Object exportDateObj = export.get("exportDate");
            if (exportDateObj == null)
                continue;

            LocalDate exportDate = LocalDate.parse(exportDateObj.toString());
            Double totalAmount = getDoubleValue(export, "totalAmount", 0.0);

            if (exportDate.isAfter(midDate) || exportDate.isEqual(midDate)) {
                currentRevenue += totalAmount;
            } else {
                previousRevenue += totalAmount;
            }
        }

        double growthRate;
        if (previousRevenue > 0) {
            growthRate = ((currentRevenue - previousRevenue) / previousRevenue) * 100;
        } else if (currentRevenue > 0) {
            // Nếu kỳ trước = 0 nhưng kỳ hiện tại > 0, thì tăng trưởng 100%
            growthRate = 100.0;
        } else {
            // Cả hai kỳ đều = 0
            growthRate = 0.0;
        }

        String trend = growthRate > 5 ? "INCREASING" : (growthRate < -5 ? "DECREASING" : "STABLE");
        String reason = generateRevenueReason(trend, growthRate, currentRevenue, previousRevenue);

        SalesInsightResponse.RevenueAnalysis analysis = new SalesInsightResponse.RevenueAnalysis();
        analysis.setTrend(trend);
        analysis.setGrowthRate(growthRate);
        analysis.setReason(reason);
        analysis.setCurrentRevenue(currentRevenue);
        analysis.setPreviousRevenue(previousRevenue);
        return analysis;
    }

    private String generateRevenueReason(String trend, double growthRate, double current, double previous) {
        if ("INCREASING".equals(trend)) {
            return String.format("Doanh thu tăng %.1f%% so với kỳ trước, cho thấy xu hướng tích cực. " +
                    "Có thể do: sản phẩm mới, chiến dịch marketing hiệu quả, hoặc mùa cao điểm.", growthRate);
        } else if ("DECREASING".equals(trend)) {
            return String.format("Doanh thu giảm %.1f%% so với kỳ trước. " +
                    "Cần xem xét: chất lượng sản phẩm, giá cả, cạnh tranh, hoặc mùa thấp điểm.", Math.abs(growthRate));
        } else {
            return "Doanh thu ổn định, không có biến động lớn.";
        }
    }

    private List<SalesInsightResponse.TopProduct> calculateTopProducts(
            List<Map<String, Object>> exports, Map<Long, Map<String, Object>> productMap) {
        Map<Long, ProductSales> productSalesMap = new HashMap<>();

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
                        Long productId = getLongValue(itemMap, "productId");
                        Integer quantity = getIntegerValue(itemMap, "quantity", 0);
                        Double unitPrice = getDoubleValue(itemMap, "unitPrice", 0.0);
                        Double discount = getDoubleValue(itemMap, "discountPercent", 0.0);

                        double revenue = quantity * unitPrice * (1 - discount / 100.0);

                        productSalesMap.computeIfAbsent(productId, k -> new ProductSales()).add(quantity, revenue);
                    }
                }
            }
        }

        List<SalesInsightResponse.TopProduct> topProductsList = productSalesMap.entrySet().stream()
                .map(entry -> {
                    Long productId = entry.getKey();
                    ProductSales sales = entry.getValue();
                    Map<String, Object> product = productMap.get(productId);
                    if (product == null)
                        return null;

                    SalesInsightResponse.TopProduct topProduct = new SalesInsightResponse.TopProduct();
                    topProduct.setProductId(productId);
                    topProduct.setProductCode(String.valueOf(product.getOrDefault("code", "")));
                    topProduct.setProductName(String.valueOf(product.getOrDefault("name", "")));
                    topProduct.setRevenue(sales.totalRevenue);
                    topProduct.setQuantitySold(sales.totalQuantity);
                    return topProduct;
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> Double.compare(b.getRevenue(), a.getRevenue()))
                .limit(10)
                .collect(Collectors.toList());

        // Set rank
        for (int i = 0; i < topProductsList.size(); i++) {
            topProductsList.get(i).setRank(i + 1);
        }

        return topProductsList;
    }

    private List<SalesInsightResponse.DecliningProduct> findDecliningProducts(
            List<Map<String, Object>> exports, Map<Long, Map<String, Object>> productMap, int days) {
        int midPoint = days / 2;
        LocalDate midDate = LocalDate.now().minusDays(midPoint);

        Map<Long, Double> currentRevenue = new HashMap<>();
        Map<Long, Double> previousRevenue = new HashMap<>();

        for (Map<String, Object> export : exports) {
            // Chỉ tính các phiếu xuất đã được xác nhận (status = "EXPORTED")
            Object status = export.get("status");
            if (status == null || !"EXPORTED".equals(status.toString())) {
                continue;
            }

            Object exportDateObj = export.get("exportDate");
            if (exportDateObj == null)
                continue;

            LocalDate exportDate = LocalDate.parse(exportDateObj.toString());
            boolean isCurrent = exportDate.isAfter(midDate) || exportDate.isEqual(midDate);

            Object items = export.get("items");
            if (items instanceof List) {
                for (Object item : (List<?>) items) {
                    if (item instanceof Map) {
                        Map<String, Object> itemMap = (Map<String, Object>) item;
                        Long productId = getLongValue(itemMap, "productId");
                        Integer quantity = getIntegerValue(itemMap, "quantity", 0);
                        Double unitPrice = getDoubleValue(itemMap, "unitPrice", 0.0);
                        Double discount = getDoubleValue(itemMap, "discountPercent", 0.0);
                        double revenue = quantity * unitPrice * (1 - discount / 100.0);

                        if (isCurrent) {
                            currentRevenue.merge(productId, revenue, Double::sum);
                        } else {
                            previousRevenue.merge(productId, revenue, Double::sum);
                        }
                    }
                }
            }
        }

        List<SalesInsightResponse.DecliningProduct> declining = new ArrayList<>();
        for (Long productId : previousRevenue.keySet()) {
            double prev = previousRevenue.get(productId);
            double curr = currentRevenue.getOrDefault(productId, 0.0);

            if (prev > 0 && curr < prev * 0.7) { // Giảm hơn 30%
                double declinePercent = ((prev - curr) / prev) * 100;
                Map<String, Object> product = productMap.get(productId);
                if (product != null) {
                    SalesInsightResponse.DecliningProduct dp = new SalesInsightResponse.DecliningProduct();
                    dp.setProductId(productId);
                    dp.setProductCode(String.valueOf(product.getOrDefault("code", "")));
                    dp.setProductName(String.valueOf(product.getOrDefault("name", "")));
                    dp.setRevenueDecline(declinePercent);
                    dp.setReason(String.format(
                            "Doanh thu giảm %.1f%% so với kỳ trước. Có thể do: cạnh tranh, giá cả, hoặc nhu cầu giảm.",
                            declinePercent));
                    declining.add(dp);
                }
            }
        }

        return declining.stream()
                .sorted((a, b) -> Double.compare(b.getRevenueDecline(), a.getRevenueDecline()))
                .limit(10)
                .collect(Collectors.toList());
    }

    private SalesInsightResponse.BestSellingHours analyzeBestSellingHours(List<Map<String, Object>> exports) {
        Map<Integer, HourStats> hourStats = new HashMap<>();
        for (int i = 0; i < 24; i++) {
            hourStats.put(i, new HourStats());
        }

        for (Map<String, Object> export : exports) {
            // Chỉ tính các phiếu xuất đã được xác nhận (status = "EXPORTED")
            Object status = export.get("status");
            if (status == null || !"EXPORTED".equals(status.toString())) {
                continue;
            }

            Object createdAtObj = export.get("createdAt");
            if (createdAtObj == null)
                continue;

            try {
                LocalDateTime createdAt = LocalDateTime.parse(createdAtObj.toString(),
                        DateTimeFormatter.ISO_DATE_TIME);
                int hour = createdAt.getHour();
                Double totalAmount = getDoubleValue(export, "totalAmount", 0.0);

                HourStats stats = hourStats.get(hour);
                stats.addRevenue(totalAmount);
                stats.incrementOrders();
            } catch (Exception e) {
                log.debug("Failed to parse date", e);
            }
        }

        List<SalesInsightResponse.HourSales> hourlyData = hourStats.entrySet().stream()
                .map(entry -> {
                    SalesInsightResponse.HourSales hourSales = new SalesInsightResponse.HourSales();
                    hourSales.setHour(entry.getKey());
                    hourSales.setRevenue(entry.getValue().totalRevenue);
                    hourSales.setOrderCount(entry.getValue().orderCount);
                    return hourSales;
                })
                .sorted((a, b) -> Double.compare(b.getRevenue(), a.getRevenue()))
                .collect(Collectors.toList());

        // Tìm giờ bán tốt nhất (top 3)
        String peakHours = hourlyData.stream()
                .limit(3)
                .map(h -> String.valueOf(h.getHour()))
                .collect(Collectors.joining(", "));

        SalesInsightResponse.BestSellingHours bestHours = new SalesInsightResponse.BestSellingHours();
        bestHours.setHourlyData(hourlyData);
        bestHours.setPeakHours("Giờ " + peakHours);
        return bestHours;
    }

    private List<SalesInsightResponse.SeasonalProduct> analyzeSeasonalProducts(
            List<Map<String, Object>> exports, Map<Long, Map<String, Object>> productMap) {
        // Phân tích đơn giản: sản phẩm có doanh số cao trong tháng hiện tại
        // Có thể mở rộng để phân tích theo mùa thực tế
        Map<Long, Double> monthlyRevenue = new HashMap<>();

        int currentMonth = LocalDate.now().getMonthValue();
        for (Map<String, Object> export : exports) {
            // Chỉ tính các phiếu xuất đã được xác nhận (status = "EXPORTED")
            Object status = export.get("status");
            if (status == null || !"EXPORTED".equals(status.toString())) {
                continue;
            }

            Object exportDateObj = export.get("exportDate");
            if (exportDateObj == null)
                continue;

            LocalDate exportDate = LocalDate.parse(exportDateObj.toString());
            if (exportDate.getMonthValue() == currentMonth) {
                Object items = export.get("items");
                if (items instanceof List) {
                    for (Object item : (List<?>) items) {
                        if (item instanceof Map) {
                            Map<String, Object> itemMap = (Map<String, Object>) item;
                            Long productId = getLongValue(itemMap, "productId");
                            Integer quantity = getIntegerValue(itemMap, "quantity", 0);
                            Double unitPrice = getDoubleValue(itemMap, "unitPrice", 0.0);
                            double revenue = quantity * unitPrice;
                            monthlyRevenue.merge(productId, revenue, Double::sum);
                        }
                    }
                }
            }
        }

        // Top 5 sản phẩm trong tháng
        return monthlyRevenue.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(entry -> {
                    Long productId = entry.getKey();
                    Map<String, Object> product = productMap.get(productId);
                    if (product == null)
                        return null;

                    SalesInsightResponse.SeasonalProduct sp = new SalesInsightResponse.SeasonalProduct();
                    sp.setProductId(productId);
                    sp.setProductCode(String.valueOf(product.getOrDefault("code", "")));
                    sp.setProductName(String.valueOf(product.getOrDefault("name", "")));
                    sp.setSeason("ALL_YEAR"); // Có thể cải thiện để phân tích theo mùa thực tế
                    sp.setSeasonalMultiplier(1.0);
                    return sp;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String generateOverallAnalysis(
            SalesInsightResponse.RevenueAnalysis revenue,
            List<SalesInsightResponse.TopProduct> topProducts,
            List<SalesInsightResponse.DecliningProduct> declining,
            int days) {
        try {
            StringBuilder context = new StringBuilder("Phân tích lịch sử bán hàng " + days + " ngày gần nhất:\n\n");
            context.append("1. Xu hướng doanh thu: ").append(revenue.getTrend())
                    .append(" (").append(String.format("%.1f", revenue.getGrowthRate())).append("%)\n");
            context.append("   Lý do: ").append(revenue.getReason()).append("\n\n");

            context.append("2. Top sản phẩm bán chạy:\n");
            topProducts.stream().limit(5).forEach(p -> {
                context.append(String.format("   - %s: %.0f VNĐ (%d sản phẩm)\n",
                        p.getProductName(), p.getRevenue(), p.getQuantitySold()));
            });
            context.append("\n");

            if (!declining.isEmpty()) {
                context.append("3. Sản phẩm giảm doanh số:\n");
                declining.stream().limit(3).forEach(p -> {
                    context.append(String.format("   - %s: Giảm %.1f%%\n",
                            p.getProductName(), p.getRevenueDecline()));
                });
            }

            String prompt = "Bạn là chuyên viên phân tích bán hàng. " +
                    "Hãy phân tích dữ liệu sau và đưa ra nhận định tổng quan (2-3 câu) về tình hình bán hàng:\n\n" +
                    context.toString();

            return geminiService.invokeGemini(prompt);
        } catch (Exception e) {
            log.warn("Failed to generate AI analysis", e);
            return String.format(
                    "Phân tích %d ngày: Doanh thu %s (%.1f%%). Top sản phẩm: %s. %d sản phẩm đang giảm doanh số.",
                    days, revenue.getTrend(), revenue.getGrowthRate(),
                    topProducts.isEmpty() ? "N/A" : topProducts.get(0).getProductName(),
                    declining.size());
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

    // Helper class
    private static class ProductSales {
        int totalQuantity = 0;
        double totalRevenue = 0.0;

        void add(int quantity, double revenue) {
            this.totalQuantity += quantity;
            this.totalRevenue += revenue;
        }
    }

    private static class HourStats {
        double totalRevenue = 0.0;
        int orderCount = 0;

        void addRevenue(double revenue) {
            this.totalRevenue += revenue;
        }

        void incrementOrders() {
            this.orderCount++;
        }
    }
}
