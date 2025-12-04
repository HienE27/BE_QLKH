package com.example.aiservice.service;

import com.example.aiservice.dto.SmartInventoryAlertResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartInventoryAlertService {

    private final WebClient.Builder webClientBuilder;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${api.gateway.url:http://api-gateway:8080}")
    private String apiGatewayUrl;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * Phân tích tồn kho và tốc độ bán để đưa ra cảnh báo thông minh
     */
    public SmartInventoryAlertResponse analyzeInventoryAlerts(String token) {
        try {
            log.info("Bắt đầu phân tích cảnh báo tồn kho");
            // Lấy dữ liệu tồn kho
            List<Map<String, Object>> stocks = fetchStocks(token);
            log.info("Đã lấy {} bản ghi tồn kho", stocks.size());
            // Lấy dữ liệu sản phẩm
            List<Map<String, Object>> products = fetchProducts(token);
            log.info("Đã lấy {} sản phẩm", products.size());
            // Lấy lịch sử xuất (bán hàng) 30 ngày gần nhất
            List<Map<String, Object>> exports = fetchExports(token, LocalDate.now().minusDays(30), LocalDate.now());
            log.info("Đã lấy {} phiếu xuất", exports.size());

            // Tạo map productId -> product info
            Map<Long, Map<String, Object>> productMap = products.stream()
                    .collect(Collectors.toMap(
                            p -> Long.valueOf(p.get("id").toString()),
                            p -> p,
                            (a, b) -> a));

            // Tính tốc độ bán trung bình cho mỗi sản phẩm
            Map<Long, Double> avgDailySales = calculateAverageDailySales(exports, productMap);

            // Tổng hợp tồn kho theo productId (tổng hợp từ tất cả các kho)
            Map<Long, Integer> totalStockByProduct = new HashMap<>();
            log.debug("Đang xử lý {} bản ghi tồn kho", stocks.size());
            for (Map<String, Object> stock : stocks) {
                Object productIdObj = stock.get("productId");
                if (productIdObj == null) {
                    log.warn("Bản ghi tồn kho thiếu productId: {}", stock);
                    continue;
                }

                Long productId;
                try {
                    productId = Long.valueOf(productIdObj.toString());
                } catch (NumberFormatException e) {
                    log.warn("productId không hợp lệ trong tồn kho: {}", productIdObj);
                    continue;
                }

                Integer quantity = getIntegerValue(stock, "quantity", 0);
                // Tổng hợp tất cả tồn kho, kể cả = 0
                totalStockByProduct.merge(productId, quantity, Integer::sum);
            }
            log.debug("Tổng hợp tồn kho theo sản phẩm: {} sản phẩm có dữ liệu tồn kho", totalStockByProduct.size());

            List<SmartInventoryAlertResponse.InventoryAlert> alerts = new ArrayList<>();
            Set<Long> processedProducts = new HashSet<>(); // Để tránh trùng lặp

            // Phân tích từng sản phẩm (theo productId, không theo từng kho)
            // Chỉ xử lý các sản phẩm có trong productMap
            for (Long productId : productMap.keySet()) {
                if (processedProducts.contains(productId)) {
                    continue; // Đã xử lý sản phẩm này rồi
                }
                processedProducts.add(productId);

                Map<String, Object> product = productMap.get(productId);
                if (product == null) {
                    continue;
                }

                // Tổng tồn kho của sản phẩm (từ tất cả các kho)
                // Nếu không có trong totalStockByProduct thì tồn kho = 0
                Integer totalStock = totalStockByProduct.getOrDefault(productId, 0);
                Double dailySales = avgDailySales.getOrDefault(productId, 0.0);

                // Ưu tiên cảnh báo nghiêm trọng nhất trước
                // Mỗi sản phẩm chỉ tạo 1 cảnh báo duy nhất (ưu tiên cảnh báo nghiêm trọng nhất)
                boolean hasCriticalAlert = false;
                SmartInventoryAlertResponse.InventoryAlert primaryAlert = null;

                // Hết hàng (ưu tiên cao nhất)
                if (totalStock == 0) {
                    primaryAlert = createAlert(
                            "OUT_OF_STOCK",
                            "CRITICAL",
                            productId,
                            product,
                            0,
                            null,
                            dailySales,
                            "Sản phẩm đã hết hàng!",
                            "Cần nhập lại ngay lập tức");
                    hasCriticalAlert = true;
                }
                // Sản phẩm sắp hết (ưu tiên cao)
                // Ngưỡng: Nếu tồn kho <= 14 ngày bán thì cảnh báo "sắp hết hàng"
                // Tức là: Tồn kho > 14 ngày bán → KHÔNG có cảnh báo
                else if (totalStock > 0 && dailySales > 0) {
                    int daysRemaining = (int) Math.ceil(totalStock / dailySales);
                    // Cảnh báo nếu còn <= 14 ngày
                    if (daysRemaining <= 14) {
                        // Phân loại mức độ:
                        // - <= 3 ngày: CRITICAL (rất nghiêm trọng)
                        // - <= 7 ngày: WARNING (cảnh báo)
                        // - > 7 ngày nhưng <= 14 ngày: INFO (thông tin)
                        String severity;
                        if (daysRemaining <= 3) {
                            severity = "CRITICAL";
                        } else if (daysRemaining <= 7) {
                            severity = "WARNING";
                        } else {
                            severity = "INFO"; // 8-14 ngày: thông tin, chưa quá nghiêm trọng
                        }

                        primaryAlert = createAlert(
                                "LOW_STOCK",
                                severity,
                                productId,
                                product,
                                totalStock,
                                daysRemaining,
                                dailySales,
                                String.format("Sản phẩm sắp hết! Dự đoán còn %d ngày. Tốc độ bán: %.1f/ngày",
                                        daysRemaining, dailySales),
                                String.format("Nên nhập lại sớm, đề xuất nhập thêm %d sản phẩm",
                                        (int) (dailySales * 14)));
                        hasCriticalAlert = (daysRemaining <= 7); // Chỉ đánh dấu critical nếu <= 7 ngày
                    }
                }

                // Hàng bán chậm bất thường (chỉ hiển thị nếu không có cảnh báo nghiêm trọng)
                if (!hasCriticalAlert && totalStock > 0 && dailySales == 0) {
                    // Kiểm tra xem KHÔNG có bán trong 30 ngày (tức là bán chậm)
                    boolean noSalesIn30Days = exports.stream()
                            .flatMap(exp -> {
                                Object items = exp.get("items");
                                if (items instanceof List) {
                                    return ((List<?>) items).stream()
                                            .filter(item -> item instanceof Map)
                                            .map(item -> (Map<String, Object>) item)
                                            .filter(item -> Objects.equals(
                                                    getLongValue((Map<String, Object>) item, "productId"), productId));
                                }
                                return java.util.stream.Stream.empty();
                            })
                            .findAny()
                            .isEmpty();

                    if (noSalesIn30Days) {
                        primaryAlert = createAlert(
                                "SLOW_SELLING",
                                "WARNING",
                                productId,
                                product,
                                totalStock,
                                null,
                                0.0,
                                "Hàng bán chậm bất thường - không có bán trong 30 ngày gần nhất",
                                "Xem xét giảm giá hoặc quảng bá sản phẩm");
                    }
                }

                // Hàng bán nhanh (chỉ hiển thị nếu không có cảnh báo sắp hết/hết hàng)
                // Vì nếu sắp hết thì chắc chắn là bán nhanh rồi, không cần cảnh báo riêng
                if (!hasCriticalAlert && primaryAlert == null && dailySales > 0
                        && isFastSelling(dailySales, totalStock)) {
                    primaryAlert = createAlert(
                            "FAST_SELLING",
                            "INFO",
                            productId,
                            product,
                            totalStock,
                            null,
                            dailySales,
                            String.format("Hàng bán nhanh! Tốc độ bán: %.1f/ngày", dailySales),
                            "Xem xét tăng mức tồn kho để đáp ứng nhu cầu");
                }

                // Chỉ thêm cảnh báo nếu có (mỗi sản phẩm chỉ có 1 cảnh báo)
                if (primaryAlert != null) {
                    alerts.add(primaryAlert);
                }
            }

            // Sắp xếp theo mức độ nghiêm trọng
            final Map<String, Integer> severityOrderMap = new HashMap<>();
            severityOrderMap.put("CRITICAL", 0);
            severityOrderMap.put("WARNING", 1);
            severityOrderMap.put("INFO", 2);
            alerts.sort((a, b) -> {
                int aOrder = severityOrderMap.getOrDefault(a.getSeverity(), 3);
                int bOrder = severityOrderMap.getOrDefault(b.getSeverity(), 3);
                return aOrder - bOrder;
            });

            // Tạo summary bằng AI
            String summary = generateAlertSummary(alerts);

            return new SmartInventoryAlertResponse(alerts, summary);

        } catch (Exception e) {
            log.error("Lỗi khi phân tích cảnh báo tồn kho", e);
            throw new RuntimeException("Không thể phân tích cảnh báo tồn kho: " + e.getMessage());
        }
    }

    private SmartInventoryAlertResponse.InventoryAlert createAlert(
            String type, String severity, Long productId, Map<String, Object> product,
            Integer currentStock, Integer daysRemaining, Double avgDailySales,
            String message, String recommendation) {
        SmartInventoryAlertResponse.InventoryAlert alert = new SmartInventoryAlertResponse.InventoryAlert();
        alert.setType(type);
        alert.setSeverity(severity);
        alert.setProductId(productId);
        alert.setProductCode(String.valueOf(product.getOrDefault("code", "")));
        alert.setProductName(String.valueOf(product.getOrDefault("name", "")));
        alert.setCurrentStock(currentStock);
        alert.setPredictedDaysRemaining(daysRemaining);
        alert.setAvgDailySales(avgDailySales);
        alert.setMessage(message);
        alert.setRecommendation(recommendation);
        return alert;
    }

    /**
     * Tính tốc độ bán trung bình/ngày cho mỗi sản phẩm
     * Dựa trên lịch sử xuất (exports) trong 30 ngày gần nhất
     * 
     * Logic: Tổng số lượng bán / Tổng số ngày trong khoảng thời gian (30 ngày)
     * Điều này đảm bảo tốc độ bán phản ánh đúng xu hướng dài hạn, không bị phóng
     * đại
     * nếu chỉ có vài ngày có bán hàng.
     */
    private Map<Long, Double> calculateAverageDailySales(
            List<Map<String, Object>> exports, Map<Long, Map<String, Object>> productMap) {
        Map<Long, Integer> totalSold = new HashMap<>();

        // Sử dụng khoảng thời gian cố định: 30 ngày
        // Điều này đảm bảo tốc độ bán được tính đều, không phụ thuộc vào số ngày có bán
        final int PERIOD_DAYS = 30;

        for (Map<String, Object> export : exports) {
            Object items = export.get("items");
            if (items instanceof List) {
                for (Object item : (List<?>) items) {
                    if (item instanceof Map) {
                        Map<String, Object> itemMap = (Map<String, Object>) item;
                        Long productId = getLongValue(itemMap, "productId");
                        if (productId != null) {
                            Integer quantity = getIntegerValue(itemMap, "quantity", 0);
                            totalSold.merge(productId, quantity, Integer::sum);
                        }
                    }
                }
            }
        }

        // Tính tốc độ bán = Tổng số lượng bán / Số ngày trong khoảng thời gian (30
        // ngày)
        // Điều này đảm bảo tốc độ bán phản ánh đúng xu hướng, không bị phóng đại
        Map<Long, Double> avgSales = new HashMap<>();
        for (Map.Entry<Long, Integer> entry : totalSold.entrySet()) {
            double avgDaily = entry.getValue() / (double) PERIOD_DAYS;
            avgSales.put(entry.getKey(), avgDaily);
            log.debug("Sản phẩm {}: Tổng bán = {} trong {} ngày, Trung bình = {}/ngày",
                    entry.getKey(), entry.getValue(), PERIOD_DAYS, String.format("%.2f", avgDaily));
        }

        return avgSales;
    }

    private boolean isFastSelling(Double dailySales, Integer currentStock) {
        // Bán nhanh nếu tốc độ bán > 10/ngày hoặc tồn kho < 3 ngày bán
        return dailySales > 10 || (currentStock > 0 && currentStock / dailySales < 3);
    }

    private String generateAlertSummary(List<SmartInventoryAlertResponse.InventoryAlert> alerts) {
        if (alerts.isEmpty()) {
            return "Không có cảnh báo tồn kho nào.";
        }

        long critical = alerts.stream().filter(a -> "CRITICAL".equals(a.getSeverity())).count();
        long warning = alerts.stream().filter(a -> "WARNING".equals(a.getSeverity())).count();
        long info = alerts.stream().filter(a -> "INFO".equals(a.getSeverity())).count();

        return String.format(
                "Tổng cộng %d cảnh báo: %d nghiêm trọng, %d cảnh báo, %d thông tin. " +
                        "Vui lòng xem chi tiết từng cảnh báo để có hành động phù hợp.",
                alerts.size(), critical, warning, info);
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
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> stockList = (List<Map<String, Object>>) response.get("data");
                log.debug("Đã lấy {} bản ghi tồn kho từ API", stockList != null ? stockList.size() : 0);
                return stockList != null ? stockList : new ArrayList<>();
            }
            log.warn("Không có trường 'data' trong phản hồi tồn kho");
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Lỗi khi lấy tồn kho từ {}: {}", apiGatewayUrl + "/api/stocks", e.getMessage(), e);
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
            log.error("Lỗi khi lấy danh sách sản phẩm", e);
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
            log.error("Lỗi khi lấy danh sách phiếu xuất", e);
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
