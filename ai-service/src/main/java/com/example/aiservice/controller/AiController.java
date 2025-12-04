package com.example.aiservice.controller;

import com.example.aiservice.common.ApiResponse;
import com.example.aiservice.dto.*;
import com.example.aiservice.service.DataService;
import com.example.aiservice.service.EmbeddingService;
import com.example.aiservice.service.GeminiService;
import com.example.aiservice.service.InventoryMilvusService;
import com.example.aiservice.service.ProductDescriptionParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final GeminiService geminiService;
    private final ProductDescriptionParser descriptionParser;
    private final DataService dataService;
    private final EmbeddingService embeddingService;
    private final InventoryMilvusService inventoryMilvusService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(
            @Valid @RequestBody AiChatRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Received chat request: {}", request.getMessage());
        try {
            // Lấy token từ header
            String token = authHeader != null && authHeader.startsWith("Bearer ") 
                ? authHeader.substring(7) 
                : null;

            String userMessage = request.getMessage().toLowerCase(Locale.ROOT);
            StringBuilder contextData = new StringBuilder();

            // Phân tích câu hỏi và lấy dữ liệu liên quan
            if (token != null) {
                boolean needProducts = false;
                boolean needInventory = false;
                boolean needOrders = false;
                
                // Nhận diện câu hỏi về số lượng, tổng số sản phẩm
                if (userMessage.contains("có bao nhiêu") || userMessage.contains("bao nhiêu sản phẩm") ||
                    userMessage.contains("tổng số") || userMessage.contains("số lượng") ||
                    userMessage.contains("hiện tại") && userMessage.contains("sản phẩm")) {
                    needProducts = true;
                    needInventory = true; // Cần cả thống kê tồn kho
                }
                
                // Nhận diện câu hỏi về tồn kho
                if (userMessage.contains("tồn kho") || userMessage.contains("inventory") || 
                    userMessage.contains("hết hàng") || userMessage.contains("sắp hết") ||
                    userMessage.contains("cảnh báo") || userMessage.contains("warning") ||
                    userMessage.contains("trong kho")) {
                    needInventory = true;
                }
                
                // Nhận diện tìm kiếm sản phẩm cụ thể
                if (userMessage.contains("tìm") || userMessage.contains("search")) {
                    String keyword = extractKeyword(userMessage);
                    if (!keyword.isEmpty() && keyword.length() > 2) {
                        String productData = dataService.searchProducts(keyword, token);
                        contextData.append("DỮ LIỆU SẢN PHẨM:\n").append(productData).append("\n\n");
                    } else {
                        needProducts = true;
                    }
                }
                
                // Nhận diện câu hỏi về sản phẩm (không phải tìm kiếm)
                if (userMessage.contains("sản phẩm") || userMessage.contains("product")) {
                    if (!userMessage.contains("tìm") && !userMessage.contains("search")) {
                        needProducts = true;
                    }
                }
                
                // Nhận diện câu hỏi về đơn hàng, doanh thu
                if (userMessage.contains("đơn hàng") || userMessage.contains("order") || 
                    userMessage.contains("doanh thu") || userMessage.contains("revenue") ||
                    userMessage.contains("bán hàng") || userMessage.contains("sales")) {
                    needOrders = true;
                }
                
                // Lấy dữ liệu theo nhu cầu
                if (needProducts && !contextData.toString().contains("DỮ LIỆU SẢN PHẨM")) {
                    String productsSummary = dataService.getProductsSummary(token);
                    contextData.append("DỮ LIỆU SẢN PHẨM:\n").append(productsSummary).append("\n\n");
                }
                
                if (needInventory && !contextData.toString().contains("DỮ LIỆU TỒN KHO")) {
                    String inventoryData = dataService.getInventorySummary(token);
                    contextData.append("DỮ LIỆU TỒN KHO:\n").append(inventoryData).append("\n\n");
                }
                
                if (needOrders && !contextData.toString().contains("DỮ LIỆU ĐƠN HÀNG")) {
                    String ordersData = dataService.getOrdersSummary(token);
                    contextData.append("DỮ LIỆU ĐƠN HÀNG:\n").append(ordersData).append("\n\n");
                }
                
                // Nếu không có keyword cụ thể và câu hỏi ngắn, lấy tổng quan
                if (contextData.isEmpty() && (
                    userMessage.contains("tổng quan") || userMessage.contains("overview") ||
                    userMessage.contains("thống kê") || userMessage.contains("statistics") ||
                    userMessage.length() < 30 // Câu hỏi ngắn, có thể cần tổng quan
                )) {
                    String inventoryData = dataService.getInventorySummary(token);
                    String ordersData = dataService.getOrdersSummary(token);
                    contextData.append("DỮ LIỆU TỒN KHO:\n").append(inventoryData).append("\n\n");
                    contextData.append("DỮ LIỆU ĐƠN HÀNG:\n").append(ordersData).append("\n\n");
                }
            } else {
                log.warn("No authentication token provided, cannot fetch database data");
            }

            String systemPrompt = """
                    Bạn là trợ lý AI thông minh trong hệ thống quản lý kho hàng (CMS).
                    Bạn có quyền truy cập dữ liệu THỰC TẾ từ database của hệ thống.
                    
                    QUY TẮC QUAN TRỌNG:
                    1. LUÔN sử dụng dữ liệu thực tế được cung cấp trong phần "DỮ LIỆU" bên dưới
                    2. Trả lời dựa trên SỐ LIỆU CỤ THỂ từ database, KHÔNG đoán mò
                    3. Nếu có dữ liệu, hãy trích dẫn số liệu chính xác
                    4. Nếu không có dữ liệu, hãy nói rõ "Không có dữ liệu trong hệ thống"
                    5. KHÔNG đưa ra hướng dẫn chung chung nếu đã có dữ liệu thực tế
                    
                    Nhiệm vụ của bạn:
                    
                    1. TRẢ LỜI VỀ SỐ LƯỢNG SẢN PHẨM:
                       - Nếu được hỏi "có bao nhiêu sản phẩm", hãy đọc số liệu từ "DỮ LIỆU TỒN KHO"
                       - Trả lời: "Hiện tại trong kho có [X] sản phẩm" (dựa trên số liệu thực tế)
                       - Liệt kê chi tiết nếu cần
                    
                    2. TÌM SẢN PHẨM: Sử dụng dữ liệu sản phẩm thực tế
                       - Liệt kê các sản phẩm phù hợp với từ khóa
                       - Hiển thị mã, tên, tồn kho, giá từ dữ liệu
                    
                    3. PHÂN TÍCH TỒN KHO: Sử dụng dữ liệu tồn kho thực tế
                       - Báo cáo tình trạng tồn kho hiện tại với số liệu cụ thể
                       - Cảnh báo sản phẩm sắp hết, hết hàng dựa trên dữ liệu
                       - Phân tích rủi ro và đề xuất giải pháp
                    
                    4. PHÂN TÍCH DOANH THU: Sử dụng dữ liệu đơn hàng thực tế
                       - Thống kê doanh thu, số đơn hàng với số liệu cụ thể
                       - Phân tích xu hướng bán hàng
                    
                    5. GỢI Ý MARKETING: Dựa trên dữ liệu thực tế
                       - Sản phẩm tồn kho cao → gợi ý giảm giá
                       - Sản phẩm bán chạy → gợi ý đẩy mạnh quảng cáo
                    
                    VÍ DỤ:
                    - Câu hỏi: "Có bao nhiêu sản phẩm trong kho?"
                      → Đọc "DỮ LIỆU TỒN KHO" → Trả lời: "Hiện tại trong kho có [X] sản phẩm (theo dữ liệu: Tổng số sản phẩm: X)"
                    
                    - Câu hỏi: "Thống kê tồn kho"
                      → Đọc "DỮ LIỆU TỒN KHO" → Trả lời với số liệu cụ thể: "Tổng số sản phẩm: X, Hết hàng: Y, Sắp hết: Z..."
                    
                    Trả lời ngắn gọn, rõ ràng, dùng tiếng Việt, LUÔN dựa trên dữ liệu thực tế.
                    """;

            String fullPrompt = systemPrompt;
            if (!contextData.isEmpty()) {
                fullPrompt += "\n\n" + contextData.toString();
            }
            fullPrompt += "\n\nCâu hỏi của người dùng: " + request.getMessage();
            
            log.info("Calling Gemini with prompt length: {}", fullPrompt.length());
            String text = geminiService.invokeGemini(fullPrompt);
            log.info("Gemini response received, length: {}", text != null ? text.length() : 0);
            return ApiResponse.ok(new AiChatResponse(text));
        } catch (Exception e) {
            log.error("Error in chat endpoint", e);
            throw e;
        }
    }

    /**
     * Trích xuất từ khóa tìm kiếm từ câu hỏi
     */
    private String extractKeyword(String message) {
        // Loại bỏ các từ không cần thiết
        String[] stopWords = {"tìm", "search", "sản phẩm", "product", "cho", "tôi", "bạn", 
                             "có", "nào", "là", "gì", "ở", "đâu", "?", "!", ".", ","};
        String cleaned = message;
        for (String stop : stopWords) {
            cleaned = cleaned.replaceAll("\\b" + stop + "\\b", " ").trim();
        }
        // Lấy từ dài nhất (có thể là tên sản phẩm)
        String[] words = cleaned.split("\\s+");
        String keyword = "";
        for (String word : words) {
            if (word.length() > keyword.length() && word.length() > 2) {
                keyword = word;
            }
        }
        return keyword;
    }

    @PostMapping("/product-description")
    public ApiResponse<ProductDescriptionResponse> generateDescription(
            @Valid @RequestBody ProductDescriptionRequest request) {
        String prompt = """
                Bạn là chuyên gia viết nội dung thương mại điện tử chuyên nghiệp.
                Hãy viết mô tả sản phẩm với tên: "%s".
                
                Yêu cầu:
                1. "short": Mô tả ngắn gọn (50-100 từ), tập trung vào điểm nổi bật chính
                2. "seo": Mô tả chuẩn SEO (100-200 từ), có từ khóa, dễ tìm kiếm trên Google
                3. "long": Mô tả chi tiết (200-400 từ), đầy đủ thông tin, tính năng, lợi ích
                4. "attributes": Mảng các thuộc tính gợi ý như: chất liệu, kích thước, màu sắc, 
                   mã màu (hex), thương hiệu, xuất xứ, bảo hành, trọng lượng, v.v.
                
                Trả về JSON với format:
                {
                  "short": "...",
                  "seo": "...",
                  "long": "...",
                  "attributes": ["chất liệu: ...", "size: ...", "màu: ...", "#hexcode", ...]
                }
                
                Không thêm giải thích khác ngoài JSON. Chỉ trả về JSON thuần túy.
                """.formatted(request.getName());
        String text = geminiService.invokeGemini(prompt);
        return ApiResponse.ok(descriptionParser.parse(text));
    }

    @PostMapping("/inventory-forecast")
    public ApiResponse<InventoryForecastResponse> forecast(
            @Valid @RequestBody InventoryForecastRequest request) {
        List<InventoryForecastRequest.ItemSummary> items = request.getItems();

        // Xây dựng danh sách sản phẩm với thông tin chi tiết
        StringBuilder itemsData = new StringBuilder();
        for (InventoryForecastRequest.ItemSummary item : items) {
            itemsData.append(String.format("- SKU: %s | Tên: %s | Tồn kho: %d",
                    item.getCode(), item.getName(), item.getQuantity()));
            if (item.getAvgDailySales() != null && item.getAvgDailySales() > 0) {
                double daysRemaining = item.getQuantity() / item.getAvgDailySales();
                itemsData.append(String.format(" | Bán TB/ngày: %.2f | Còn đủ: %.1f ngày",
                        item.getAvgDailySales(), daysRemaining));
            }
            itemsData.append("\n");
        }

        // Tạo embedding từ snapshot tồn kho hiện tại để search Milvus
        List<Float> currentEmbedding = null;
        List<InventoryForecastResponse.SimilarSnapshot> similarSnapshots = new ArrayList<>();
        String milvusContext = "";

        try {
            // Tạo embedding từ text mô tả snapshot
            String snapshotText = itemsData.toString();
            currentEmbedding = embeddingService.generateEmbedding(snapshotText);

            // Tìm các snapshot tương tự từ Milvus (top 3)
            List<Map<String, Object>> similarResults = inventoryMilvusService.searchSimilar(currentEmbedding, 3);
            
            if (!similarResults.isEmpty()) {
                StringBuilder contextBuilder = new StringBuilder("\n\n=== THAM KHẢO CÁC KỲ TỒN KHO TƯƠNG TỰ TRONG QUÁ KHỨ ===\n");
                
                for (Map<String, Object> result : similarResults) {
                    Double score = (Double) result.get("score");
                    String snapshotTime = (String) result.get("snapshot_time");
                    String metadataStr = (String) result.get("metadata");
                    
                    // Parse metadata để lấy summary
                    String summary = "";
                    try {
                        if (metadataStr != null && !metadataStr.isEmpty()) {
                            JsonNode metadata = objectMapper.readTree(metadataStr);
                            if (metadata.has("summary")) {
                                summary = metadata.get("summary").asText();
                            } else {
                                // Nếu không có summary, tạo từ metadata
                                summary = String.format("Snapshot từ %s (độ tương tự: %.2f)", 
                                    snapshotTime, score != null ? score : 0.0);
                            }
                        }
                    } catch (Exception e) {
                        summary = String.format("Snapshot từ %s", snapshotTime);
                    }
                    
                    // Tính độ tương tự dạng phần trăm (score càng thấp càng tương tự)
                    double similarityPercent = score != null ? Math.max(0, Math.min(100, 100 - (score * 10))) : 0;
                    
                    contextBuilder.append(String.format(
                        "- Kỳ tương tự (%.0f%%): %s\n  %s\n",
                        similarityPercent, snapshotTime, summary
                    ));
                    
                    // Thêm vào response để frontend hiển thị
                    similarSnapshots.add(new InventoryForecastResponse.SimilarSnapshot(
                        snapshotTime, score, summary
                    ));
                }
                
                milvusContext = contextBuilder.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to search Milvus for similar snapshots", e);
            // Tiếp tục mà không có Milvus context
        }

        // Xây dựng prompt với context từ Milvus
        String prompt = """
                Bạn là chuyên gia quản trị kho và dự báo time-series chuyên nghiệp.
                Phân tích dữ liệu tồn kho và dự báo nhu cầu trong 7 ngày tới.
                
                Dữ liệu sản phẩm hiện tại:
                %s
                %s
                
                Nhiệm vụ:
                1. DỰ ĐOÁN SỐ LƯỢNG SẼ THIẾU TRONG 7 NGÀY TỚI:
                   - Tính toán dựa trên tồn kho hiện tại và tốc độ bán (nếu có)
                   - Ước tính số lượng thiếu hụt
                   - Ưu tiên các SKU có nguy cơ cao nhất
                
                2. CẢNH BÁO CHI TIẾT:
                   - "SKU [mã] chỉ đủ cho [X] ngày nữa" → Tính toán chính xác số ngày
                   - "SKU [mã] sẽ hết hàng vào [ngày]" → Dự báo ngày hết hàng
                   - Cảnh báo các SKU có tồn kho < 7 ngày bán
                
                3. CẢNH BÁO DƯ HÀNG/TỒN KHO CAO:
                   - Xác định SKU có tồn kho quá cao (tồn > 30 ngày bán)
                   - Phân tích rủi ro: vốn bị đọng, hàng lỗi thời, chi phí lưu kho
                   - Gợi ý giải pháp: giảm giá, khuyến mãi, ngừng nhập
                
                4. ĐỀ XUẤT SỐ LƯỢNG CẦN NHẬP:
                   - Tính toán số lượng nhập tối ưu cho từng SKU thiếu hàng
                   - Đảm bảo đủ hàng cho ít nhất 14-30 ngày
                   - Gợi ý thời điểm nhập hàng
                
                5. THAM KHẢO CÁC KỲ TỒN KHO TƯƠNG TỰ TRONG QUÁ KHỨ (NẾU CÓ):
                   - Nếu có dữ liệu Milvus ở trên, hãy so sánh với kỳ hiện tại
                   - Phân tích điểm giống/khác và điều chỉnh khuyến nghị cho phù hợp
                   - Ví dụ: "Dựa trên kỳ tương tự từ [ngày], có thể thấy xu hướng..."
                
                Format trả lời:
                - Ngắn gọn, rõ ràng, có số liệu cụ thể
                - Ưu tiên các cảnh báo khẩn cấp trước
                - Đưa ra hành động cụ thể, có thể thực hiện ngay
                - Nếu có tham khảo từ Milvus, hãy đề cập rõ ràng
                
                Hãy phân tích và đưa ra khuyến nghị chi tiết.
                """.formatted(itemsData.toString(), milvusContext);

        String text = geminiService.invokeGemini(prompt);
        
        // Lưu snapshot hiện tại vào Milvus để dùng cho lần sau
        try {
            if (currentEmbedding != null) {
                // Tạo metadata JSON
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("totalItems", items.size());
                metadata.put("summary", String.format("Tổng %d sản phẩm", items.size()));
                
                // Tính toán thống kê
                int outOfStock = 0;
                int lowStock = 0;
                int totalQuantity = 0;
                for (InventoryForecastRequest.ItemSummary item : items) {
                    if (item.getQuantity() == 0) outOfStock++;
                    else if (item.getQuantity() <= 10) lowStock++;
                    totalQuantity += item.getQuantity();
                }
                metadata.put("outOfStock", outOfStock);
                metadata.put("lowStock", lowStock);
                metadata.put("totalQuantity", totalQuantity);
                
                String metadataJson = objectMapper.writeValueAsString(metadata);
                String snapshotText = itemsData.toString();
                inventoryMilvusService.saveSnapshot(snapshotText, currentEmbedding, metadataJson);
            }
        } catch (Exception e) {
            log.warn("Failed to save snapshot to Milvus", e);
            // Không throw, chỉ log warning
        }
        
        return ApiResponse.ok(new InventoryForecastResponse(text, similarSnapshots));
    }
}
