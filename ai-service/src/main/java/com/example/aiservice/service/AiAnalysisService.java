package com.example.aiservice.service;

import com.example.aiservice.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisService {

    private final GeminiService geminiService;
    private final DataService dataService;
    private final ObjectMapper objectMapper;

    /**
     * 1. Tạo cảnh báo thông minh cho Dashboard
     */
    public DashboardAlertsResponse generateDashboardAlerts(String token) {
        List<Map<String, Object>> products = dataService.fetchProductsListPublic(token);
        // Lấy tồn kho thực tế từ API stocks (tổng hợp từ tất cả các kho)
        Map<Long, Integer> stockMap = dataService.fetchStocksByProduct(token);
        String inventorySummary = dataService.getInventorySummary(token);
        String ordersSummary = dataService.getOrdersSummary(token);

        List<DashboardAlertsResponse.Alert> alerts = new ArrayList<>();

        // Phân tích sản phẩm dựa trên tồn kho thực tế
        int outOfStock = 0;
        int lowStock = 0;
        int highStock = 0;
        double totalValue = 0;

        for (Map<String, Object> p : products) {
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
            double price = getDoubleValue(p, "unitPrice");
            totalValue += qty * price;

            if (qty == 0) {
                outOfStock++;
            } else if (qty <= 10) {
                lowStock++;
            } else if (qty > 100) {
                highStock++;
            }
        }

        // Tạo alerts dựa trên dữ liệu
        if (outOfStock > 0) {
            alerts.add(new DashboardAlertsResponse.Alert(
                    "CRITICAL",
                    "Sản phẩm hết hàng",
                    String.format("Có %d sản phẩm đã hết hàng, cần nhập bổ sung ngay!", outOfStock),
                    "/reports/inventory-report",
                    "alert-circle"));
        }

        if (lowStock > 0) {
            alerts.add(new DashboardAlertsResponse.Alert(
                    "WARNING",
                    "Sản phẩm sắp hết",
                    String.format("Có %d sản phẩm sắp hết hàng (tồn kho ≤ 10)", lowStock),
                    "/reports/inventory-report",
                    "alert-triangle"));
        }

        if (highStock > 0) {
            alerts.add(new DashboardAlertsResponse.Alert(
                    "INFO",
                    "Tồn kho cao",
                    String.format("Có %d sản phẩm tồn kho cao (>100). Xem xét giảm giá để đẩy hàng.", highStock),
                    "/dashboard/products",
                    "package"));
        }

        // Gọi AI để tạo summary
        // Chỉ truyền số liệu thực tế đã được tính toán, không truyền inventorySummary
        // vì nó có thể chứa dữ liệu cũ
        String prompt = String.format(
                """
                        Dựa trên dữ liệu kho hàng sau, hãy tạo một đoạn tóm tắt ngắn gọn (2-3 câu) về tình trạng kho:

                        %s

                        Thống kê tồn kho hiện tại:
                        - Tổng số sản phẩm: %d
                        - Hết hàng: %d sản phẩm
                        - Sắp hết (≤10): %d sản phẩm
                        - Tồn cao (>100): %d sản phẩm
                        - Tổng giá trị tồn kho: %.0f VNĐ

                        Lưu ý: Chỉ sử dụng số liệu trên để tạo summary. KHÔNG được tạo ra mâu thuẫn hoặc thông tin không nhất quán.
                        Trả lời bằng tiếng Việt, ngắn gọn, tập trung vào điểm quan trọng nhất.
                        """,
                ordersSummary, products.size(), outOfStock, lowStock, highStock, totalValue);

        String summary = geminiService.invokeGemini(prompt);

        return new DashboardAlertsResponse(alerts, summary);
    }

    /**
     * 2. Phân tích ABC tồn kho
     */
    public ABCAnalysisResponse generateABCAnalysis(String token) {
        List<Map<String, Object>> products = dataService.fetchProductsListPublic(token);

        // Tính doanh thu cho mỗi sản phẩm (giả định = quantity * unitPrice)
        List<Map<String, Object>> productRevenues = new ArrayList<>();
        double totalRevenue = 0;

        for (Map<String, Object> p : products) {
            double price = getDoubleValue(p, "unitPrice");
            int qty = getIntValue(p, "quantity");
            double revenue = price * qty; // Giá trị tồn kho
            totalRevenue += revenue;

            Map<String, Object> item = new HashMap<>(p);
            item.put("revenue", revenue);
            productRevenues.add(item);
        }

        // Sort theo revenue giảm dần
        productRevenues.sort((a, b) -> Double.compare(
                getDoubleValue(b, "revenue"),
                getDoubleValue(a, "revenue")));

        // Phân loại ABC
        List<ABCAnalysisResponse.ProductCategory> categoryA = new ArrayList<>();
        List<ABCAnalysisResponse.ProductCategory> categoryB = new ArrayList<>();
        List<ABCAnalysisResponse.ProductCategory> categoryC = new ArrayList<>();

        double cumulativeRevenue = 0;
        for (Map<String, Object> p : productRevenues) {
            double revenue = getDoubleValue(p, "revenue");
            cumulativeRevenue += revenue;
            double percentage = totalRevenue > 0 ? (revenue / totalRevenue) * 100 : 0;
            double cumulativePercentage = totalRevenue > 0 ? (cumulativeRevenue / totalRevenue) * 100 : 0;

            ABCAnalysisResponse.ProductCategory item = new ABCAnalysisResponse.ProductCategory(
                    String.valueOf(p.getOrDefault("code", "")),
                    String.valueOf(p.getOrDefault("name", "")),
                    revenue,
                    percentage,
                    getIntValue(p, "quantity"));

            if (cumulativePercentage <= 80) {
                categoryA.add(item);
            } else if (cumulativePercentage <= 95) {
                categoryB.add(item);
            } else {
                categoryC.add(item);
            }
        }

        // Gọi AI để phân tích
        String prompt = String.format("""
                Phân tích ABC tồn kho:
                - Nhóm A (%d sản phẩm): Chiếm 80%% giá trị, ưu tiên cao nhất
                - Nhóm B (%d sản phẩm): Chiếm 15%% giá trị, ưu tiên trung bình
                - Nhóm C (%d sản phẩm): Chiếm 5%% giá trị, ưu tiên thấp
                - Tổng giá trị kho: %.0f VNĐ

                Hãy đưa ra 3-4 khuyến nghị cụ thể về quản lý tồn kho dựa trên phân tích ABC này.
                Trả lời bằng tiếng Việt, format dạng bullet points.
                """, categoryA.size(), categoryB.size(), categoryC.size(), totalRevenue);

        String analysis = geminiService.invokeGemini(prompt);

        return new ABCAnalysisResponse(
                categoryA.subList(0, Math.min(10, categoryA.size())), // Top 10 category A
                categoryB.subList(0, Math.min(10, categoryB.size())),
                categoryC.subList(0, Math.min(10, categoryC.size())),
                analysis,
                "Dựa trên phân tích ABC, tập trung quản lý chặt nhóm A, tối ưu nhóm B, và giảm thiểu nhóm C.");
    }

    /**
     * 3. Gợi ý giá bán tối ưu
     */
    public PriceSuggestionResponse generatePriceSuggestion(PriceSuggestionRequest request) {
        String prompt = String.format("""
                Bạn là chuyên gia định giá sản phẩm. Phân tích và đề xuất giá bán tối ưu:

                Thông tin sản phẩm:
                - Tên: %s
                - Mã: %s
                - Giá hiện tại: %.0f VNĐ
                - Giá vốn: %.0f VNĐ
                - Tồn kho: %d
                - Bán TB/ngày: %d
                - Số ngày tồn kho: %d ngày

                Yêu cầu trả về JSON với format:
                {
                    "suggestedPrice": [số],
                    "minPrice": [số],
                    "maxPrice": [số],
                    "strategy": "DISCOUNT" hoặc "MAINTAIN" hoặc "INCREASE",
                    "reasoning": "[giải thích ngắn gọn]",
                    "expectedProfit": [lợi nhuận dự kiến %%],
                    "promotionSuggestion": "[gợi ý khuyến mãi nếu cần]"
                }

                Chỉ trả về JSON, không có text khác.
                """,
                request.getProductName(),
                request.getProductCode(),
                request.getCurrentPrice() != null ? request.getCurrentPrice() : 0,
                request.getCostPrice() != null ? request.getCostPrice() : 0,
                request.getCurrentStock() != null ? request.getCurrentStock() : 0,
                request.getAvgDailySales() != null ? request.getAvgDailySales() : 0,
                request.getDaysInStock() != null ? request.getDaysInStock() : 0);

        String response = geminiService.invokeGemini(prompt);

        try {
            // Parse JSON response
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);

            return new PriceSuggestionResponse(
                    node.has("suggestedPrice") ? node.get("suggestedPrice").asDouble() : request.getCurrentPrice(),
                    node.has("minPrice") ? node.get("minPrice").asDouble() : request.getCurrentPrice() * 0.8,
                    node.has("maxPrice") ? node.get("maxPrice").asDouble() : request.getCurrentPrice() * 1.2,
                    node.has("strategy") ? node.get("strategy").asText() : "MAINTAIN",
                    node.has("reasoning") ? node.get("reasoning").asText() : "Giữ nguyên giá hiện tại",
                    node.has("expectedProfit") ? node.get("expectedProfit").asDouble() : 20.0,
                    node.has("promotionSuggestion") ? node.get("promotionSuggestion").asText() : "");
        } catch (Exception e) {
            log.error("Error parsing price suggestion response", e);
            return new PriceSuggestionResponse(
                    request.getCurrentPrice(),
                    request.getCurrentPrice() * 0.8,
                    request.getCurrentPrice() * 1.2,
                    "MAINTAIN",
                    response,
                    20.0,
                    "");
        }
    }

    /**
     * 4. Phân tích xu hướng bán hàng
     */
    public SalesTrendResponse generateSalesTrend(String token, String period) {
        String ordersSummary = dataService.getOrdersSummary(token);
        String inventorySummary = dataService.getInventorySummary(token);

        String prompt = String.format("""
                Dựa trên dữ liệu sau, phân tích xu hướng bán hàng:

                %s
                %s

                Kỳ phân tích: %s

                Trả về JSON với format:
                {
                    "trend": "INCREASING" hoặc "DECREASING" hoặc "STABLE",
                    "growthRate": [số phần trăm],
                    "analysis": "[phân tích 2-3 câu]",
                    "forecast": "[dự báo ngắn gọn]",
                    "topProducts": ["sản phẩm 1", "sản phẩm 2"],
                    "recommendations": ["khuyến nghị 1", "khuyến nghị 2"]
                }

                Chỉ trả về JSON, không có text khác.
                """, ordersSummary, inventorySummary, period);

        String response = geminiService.invokeGemini(prompt);

        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);

            List<String> topProducts = new ArrayList<>();
            if (node.has("topProducts")) {
                node.get("topProducts").forEach(p -> topProducts.add(p.asText()));
            }

            List<String> recommendations = new ArrayList<>();
            if (node.has("recommendations")) {
                node.get("recommendations").forEach(r -> recommendations.add(r.asText()));
            }

            return new SalesTrendResponse(
                    period,
                    new ArrayList<>(), // Would need actual time-series data
                    node.has("trend") ? node.get("trend").asText() : "STABLE",
                    node.has("growthRate") ? node.get("growthRate").asDouble() : 0.0,
                    node.has("analysis") ? node.get("analysis").asText() : "Chưa đủ dữ liệu phân tích",
                    node.has("forecast") ? node.get("forecast").asText() : "Cần thêm dữ liệu để dự báo",
                    topProducts,
                    recommendations);
        } catch (Exception e) {
            log.error("Error parsing sales trend response", e);
            return new SalesTrendResponse(
                    period,
                    new ArrayList<>(),
                    "STABLE",
                    0.0,
                    response,
                    "Cần thêm dữ liệu",
                    new ArrayList<>(),
                    new ArrayList<>());
        }
    }

    /**
     * 5. Tự động tạo báo cáo
     */
    public ReportResponse generateReport(String token, ReportRequest request) {
        String inventorySummary = dataService.getInventorySummary(token);
        String ordersSummary = dataService.getOrdersSummary(token);
        String productsSummary = dataService.getProductsSummary(token);

        String reportTitle = switch (request.getReportType()) {
            case "INVENTORY" -> "Báo cáo Tồn kho";
            case "SALES" -> "Báo cáo Doanh số";
            case "IMPORT_EXPORT" -> "Báo cáo Nhập/Xuất kho";
            default -> "Báo cáo Tổng hợp";
        };

        String prompt = String.format("""
                Tạo báo cáo %s với định kỳ %s.

                Dữ liệu:
                %s
                %s
                %s

                Yêu cầu:
                1. Tạo báo cáo HTML đẹp, chuyên nghiệp
                2. Bao gồm: tiêu đề, ngày tạo, tóm tắt, số liệu chính, biểu đồ mô tả (text-based)
                3. Highlights: 3-5 điểm nổi bật
                4. Recommendations: 3-5 khuyến nghị hành động

                Trả về JSON:
                {
                    "summary": "[tóm tắt 2-3 câu]",
                    "htmlContent": "[nội dung HTML của báo cáo]",
                    "highlights": "[các điểm nổi bật]",
                    "recommendations": "[các khuyến nghị]"
                }

                Chỉ trả về JSON.
                """, reportTitle, request.getPeriod(), inventorySummary, ordersSummary, productsSummary);

        String response = geminiService.invokeGemini(prompt);

        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);

            return new ReportResponse(
                    request.getReportType(),
                    reportTitle,
                    node.has("summary") ? node.get("summary").asText() : "Báo cáo tổng hợp",
                    node.has("htmlContent") ? node.get("htmlContent").asText()
                            : generateDefaultHtml(reportTitle, inventorySummary),
                    node.has("highlights") ? node.get("highlights").asText() : "",
                    node.has("recommendations") ? node.get("recommendations").asText() : "");
        } catch (Exception e) {
            log.error("Error parsing report response", e);
            return new ReportResponse(
                    request.getReportType(),
                    reportTitle,
                    "Báo cáo được tạo tự động",
                    generateDefaultHtml(reportTitle, inventorySummary + "\n" + ordersSummary),
                    response,
                    "");
        }
    }

    /**
     * 6. Gợi ý combo sản phẩm
     */
    public ComboSuggestionResponse generateComboSuggestions(String token) {
        List<Map<String, Object>> products = dataService.fetchProductsListPublic(token);

        // Lọc sản phẩm có tồn kho
        List<Map<String, Object>> availableProducts = products.stream()
                .filter(p -> getIntValue(p, "quantity") > 0)
                .limit(20)
                .collect(Collectors.toList());

        StringBuilder productsInfo = new StringBuilder();
        for (Map<String, Object> p : availableProducts) {
            productsInfo.append(String.format("- %s (%s): Giá %.0f, Tồn %d\n",
                    p.get("code"), p.get("name"),
                    getDoubleValue(p, "unitPrice"),
                    getIntValue(p, "quantity")));
        }

        String prompt = String.format("""
                Dựa trên danh sách sản phẩm sau, đề xuất 3 combo khuyến mãi hấp dẫn:

                %s

                Yêu cầu:
                1. Mỗi combo 2-4 sản phẩm liên quan hoặc bổ sung nhau
                2. Giảm giá combo 10-20%% so với mua lẻ
                3. Ưu tiên sản phẩm tồn kho cao

                Trả về JSON:
                {
                    "combos": [
                        {
                            "name": "Tên combo",
                            "items": [{"code": "...", "name": "...", "price": 0, "quantity": 1}],
                            "originalPrice": 0,
                            "comboPrice": 0,
                            "discount": 0,
                            "reason": "Lý do gợi ý",
                            "targetCustomer": "Đối tượng khách hàng"
                        }
                    ],
                    "analysis": "Phân tích ngắn gọn"
                }

                Chỉ trả về JSON.
                """, productsInfo.toString());

        String response = geminiService.invokeGemini(prompt);

        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);

            List<ComboSuggestionResponse.ComboSuggestion> combos = new ArrayList<>();
            if (node.has("combos")) {
                for (JsonNode comboNode : node.get("combos")) {
                    List<ComboSuggestionResponse.ComboItem> items = new ArrayList<>();
                    if (comboNode.has("items")) {
                        for (JsonNode itemNode : comboNode.get("items")) {
                            items.add(new ComboSuggestionResponse.ComboItem(
                                    itemNode.has("code") ? itemNode.get("code").asText() : "",
                                    itemNode.has("name") ? itemNode.get("name").asText() : "",
                                    itemNode.has("price") ? itemNode.get("price").asDouble() : 0,
                                    itemNode.has("quantity") ? itemNode.get("quantity").asInt() : 1));
                        }
                    }

                    combos.add(new ComboSuggestionResponse.ComboSuggestion(
                            comboNode.has("name") ? comboNode.get("name").asText() : "Combo",
                            items,
                            comboNode.has("originalPrice") ? comboNode.get("originalPrice").asDouble() : 0,
                            comboNode.has("comboPrice") ? comboNode.get("comboPrice").asDouble() : 0,
                            comboNode.has("discount") ? comboNode.get("discount").asDouble() : 0,
                            comboNode.has("reason") ? comboNode.get("reason").asText() : "",
                            comboNode.has("targetCustomer") ? comboNode.get("targetCustomer").asText() : ""));
                }
            }

            return new ComboSuggestionResponse(
                    combos,
                    node.has("analysis") ? node.get("analysis").asText() : "Gợi ý combo dựa trên tồn kho");
        } catch (Exception e) {
            log.error("Error parsing combo suggestions", e);
            return new ComboSuggestionResponse(new ArrayList<>(), response);
        }
    }

    /**
     * 7. OCR hóa đơn/phiếu nhập
     */
    public ImageOCRResponse processOCR(ImageOCRRequest request) {
        String imageData = request.getImageBase64() != null ? request.getImageBase64() : request.getImageUrl();

        if (imageData == null || imageData.isEmpty()) {
            throw new IllegalArgumentException("Image data is required");
        }

        // Gemini có thể xử lý ảnh qua base64
        String prompt = String.format("""
                Phân tích hình ảnh hóa đơn/phiếu nhập này và trích xuất thông tin.

                Loại tài liệu: %s
                Hình ảnh (base64): %s

                Trả về JSON:
                {
                    "documentType": "INVOICE hoặc RECEIPT",
                    "supplierName": "Tên nhà cung cấp",
                    "invoiceNumber": "Số hóa đơn",
                    "invoiceDate": "Ngày (dd/MM/yyyy)",
                    "items": [
                        {"name": "Tên SP", "code": "Mã", "quantity": 0, "unitPrice": 0, "totalPrice": 0, "unit": "Cái"}
                    ],
                    "totalAmount": 0,
                    "confidence": 0.95
                }

                Nếu không đọc được ảnh, trả về rawText với nội dung đoán được.
                Chỉ trả về JSON.
                """,
                request.getDocumentType() != null ? request.getDocumentType() : "INVOICE",
                imageData.substring(0, Math.min(100, imageData.length())) + "...");

        // Note: Actual image processing would require Gemini Vision API
        // For now, return a placeholder response
        String response = geminiService.invokeGemini(prompt);

        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);

            List<ImageOCRResponse.ExtractedItem> items = new ArrayList<>();
            if (node.has("items")) {
                for (JsonNode itemNode : node.get("items")) {
                    items.add(new ImageOCRResponse.ExtractedItem(
                            itemNode.has("name") ? itemNode.get("name").asText() : "",
                            itemNode.has("code") ? itemNode.get("code").asText() : "",
                            itemNode.has("quantity") ? itemNode.get("quantity").asInt() : 0,
                            itemNode.has("unitPrice") ? itemNode.get("unitPrice").asDouble() : 0,
                            itemNode.has("totalPrice") ? itemNode.get("totalPrice").asDouble() : 0,
                            itemNode.has("unit") ? itemNode.get("unit").asText() : "Cái"));
                }
            }

            return new ImageOCRResponse(
                    node.has("documentType") ? node.get("documentType").asText() : "INVOICE",
                    node.has("supplierName") ? node.get("supplierName").asText() : "",
                    node.has("invoiceNumber") ? node.get("invoiceNumber").asText() : "",
                    node.has("invoiceDate") ? node.get("invoiceDate").asText() : "",
                    items,
                    node.has("totalAmount") ? node.get("totalAmount").asDouble() : 0,
                    node.has("rawText") ? node.get("rawText").asText() : "",
                    node.has("confidence") ? node.get("confidence").asDouble() : 0.0);
        } catch (Exception e) {
            log.error("Error parsing OCR response", e);
            return new ImageOCRResponse(
                    "UNKNOWN",
                    "",
                    "",
                    "",
                    new ArrayList<>(),
                    0.0,
                    response,
                    0.0);
        }
    }

    // Helper methods
    private int getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    private String extractJson(String text) {
        // Tìm JSON trong response
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String generateDefaultHtml(String title, String content) {
        return String.format("""
                <div style="font-family: Arial, sans-serif; padding: 20px;">
                    <h1 style="color: #333;">%s</h1>
                    <p style="color: #666;">Ngày tạo: %s</p>
                    <div style="white-space: pre-wrap; background: #f5f5f5; padding: 15px; border-radius: 8px;">
                        %s
                    </div>
                </div>
                """, title, LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), content);
    }
}
