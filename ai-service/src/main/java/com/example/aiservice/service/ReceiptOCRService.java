package com.example.aiservice.service;

import com.example.aiservice.dto.ReceiptOCRRequest;
import com.example.aiservice.dto.ReceiptOCRResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.*;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptOCRService {

    private final GeminiService geminiService;
    private final MilvusService milvusService;
    private final EmbeddingService embeddingService;
    private final DataService dataService;
    private final WebClient geminiWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api-key}")
    private String apiKey;

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final String MODEL_PATH = "/models/gemini-2.5-flash:generateContent";

    /**
     * Đọc ảnh phiếu nhập/xuất và trích xuất thông tin
     */
    public ReceiptOCRResponse processReceiptImage(ReceiptOCRRequest request) {
        try {
            // Bước 1: Lấy ảnh từ URL hoặc base64
            String imageData = getImageData(request);

            // Bước 2: Gọi Gemini để đọc ảnh và trích xuất thông tin
            String extractedData = extractDataFromImage(imageData, request.getReceiptType());

            // Bước 3: Parse JSON response từ Gemini
            ReceiptOCRResponse response = parseGeminiResponse(extractedData, request.getReceiptType());

            // Bước 4: Tạo embedding từ text đã trích xuất
            String searchText = buildSearchText(response);
            List<Float> embedding = embeddingService.generateEmbedding(searchText);
            log.info("Generated embedding with {} dimensions", embedding.size());

            // Bước 5: Tìm kiếm vector trong Milvus để lấy thông tin từ phiếu tương tự
            enrichWithVectorSearch(response, embedding, request.getReceiptType());

            // Bước 6: Lưu embedding vào Milvus để sử dụng sau này
            saveToMilvus(response, embedding, extractedData);

            return response;
        } catch (Exception e) {
            log.error("Error processing receipt image", e);
            throw new RuntimeException("Không thể xử lý ảnh: " + e.getMessage(), e);
        }
    }

    private String getImageData(ReceiptOCRRequest request) {
        if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            // Nếu có URL, download ảnh và convert sang base64
            try {
                byte[] imageBytes = WebClient.create()
                        .get()
                        .uri(request.getImageUrl())
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .block(Duration.ofSeconds(30));

                if (imageBytes != null) {
                    return Base64.getEncoder().encodeToString(imageBytes);
                }
            } catch (Exception e) {
                log.warn("Failed to download image from URL, trying base64", e);
            }
        }

        if (request.getImageBase64() != null && !request.getImageBase64().isBlank()) {
            // Remove data URL prefix if present
            String base64 = request.getImageBase64();
            if (base64.contains(",")) {
                base64 = base64.substring(base64.indexOf(",") + 1);
            }
            return base64;
        }

        throw new IllegalArgumentException("Cần có imageUrl hoặc imageBase64");
    }

    private String extractDataFromImage(String imageBase64, String receiptType) {
        String prompt = buildPrompt(receiptType);

        // Gọi Gemini với ảnh
        Map<String, Object> body = new HashMap<>();

        List<Map<String, Object>> parts = new ArrayList<>();

        // Text part
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", prompt);
        parts.add(textPart);

        // Image part
        Map<String, Object> imagePart = new HashMap<>();
        Map<String, Object> inlineData = new HashMap<>();
        inlineData.put("mime_type", "image/jpeg");
        inlineData.put("data", imageBase64);
        imagePart.put("inline_data", inlineData);
        parts.add(imagePart);

        Map<String, Object> content = new HashMap<>();
        content.put("role", "user");
        content.put("parts", parts);

        body.put("contents", Collections.singletonList(content));

        try {
            String response = geminiWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(MODEL_PATH)
                            .queryParam("key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            clientResponse -> clientResponse.bodyToMono(String.class).map(msg -> {
                                log.error("Gemini error response: {}", msg);
                                return new RuntimeException("Gemini API error: " + msg);
                            }))
                    .bodyToMono(String.class)
                    .block(TIMEOUT);

            // Parse response để lấy text
            JsonNode jsonResponse = objectMapper.readTree(response);
            String text = jsonResponse.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();

            return text;
        } catch (WebClientResponseException ex) {
            log.error("Gemini HTTP error {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            
            // Xử lý riêng cho lỗi 429 (Quota Exceeded)
            if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                String errorBody = ex.getResponseBodyAsString();
                log.error("Gemini API quota exceeded. Response: {}", errorBody);
                throw new RuntimeException(
                    "Đã vượt quá hạn mức sử dụng Gemini API. " +
                    "Free tier có giới hạn ~20 requests/ngày. " +
                    "Vui lòng set up billing trong Google AI Studio để tăng quota " +
                    "(https://aistudio.google.com/usage) hoặc đợi đến ngày mai để quota reset."
                );
            }
            
            throw new RuntimeException("Gemini API error: " + ex.getStatusCode());
        } catch (Exception ex) {
            log.error("Gemini invocation failed", ex);
            throw new RuntimeException("Không thể kết nối Gemini: " + ex.getMessage());
        }
    }

    private String buildPrompt(String receiptType) {
        if ("IMPORT".equals(receiptType)) {
            return """
                    Bạn là chuyên gia OCR và xử lý phiếu nhập kho.
                    Hãy đọc ảnh (có thể là phiếu nhập kho thật, form web, hoặc screenshot) và trích xuất thông tin sau đây, trả về dưới dạng JSON:

                    {
                      "supplierName": "Tên nhà cung cấp ĐẦY ĐỦ (tìm trong 'Nguồn nhập', 'Nhà cung cấp', 'Tên nhà cung cấp', hoặc 'Supplier'). QUAN TRỌNG: Đọc đầy đủ tên từ đầu đến cuối, bao gồm cả họ, tên đệm và tên. KHÔNG được cắt ngắn, KHÔNG được chỉ lấy phần cuối. Ví dụ: Nếu trong ảnh có 'Đỗ Quốc Huy' thì phải trả về 'Đỗ Quốc Huy', KHÔNG được chỉ trả về 'Huy' hoặc 'Quốc Huy'. Phải đọc toàn bộ text trong field đó",
                      "supplierPhone": "Số điện thoại nhà cung cấp (tìm trong 'Số điện thoại', 'Phone', hoặc 'SĐT')",
                      "supplierAddress": "Địa chỉ nhà cung cấp ĐẦY ĐỦ (tìm trong 'Địa chỉ', 'Address'). QUAN TRỌNG: Đọc đầy đủ địa chỉ, không bỏ sót",
                      "receiptCode": "Mã phiếu nhập (nếu có, bỏ qua nếu là 'Tự động tạo')",
                      "receiptDate": "Ngày phiếu (nếu có)",
                      "note": "Lý do nhập hoặc ghi chú (tìm trong 'Lý do nhập', 'Ghi chú', hoặc 'Note')",
                      "products": [
                        {
                          "name": "Tên sản phẩm (từ cột 'Tên hàng hóa' hoặc 'Product Name')",
                          "code": "Mã sản phẩm (từ cột 'Mã hàng' hoặc 'Product Code')",
                          "quantity": Số lượng (số nguyên, từ cột 'SL' hoặc 'Quantity'),
                          "unitPrice": Đơn giá (số thực, từ cột 'Đơn giá' hoặc 'Unit Price', bỏ dấu chấm/phẩy phân cách hàng nghìn),
                          "discount": Chiết khấu phần trăm (số thực, từ cột 'Chiết khấu (%)' hoặc 'Discount', nếu có),
                          "totalPrice": Thành tiền (số thực, từ cột 'Thành tiền' hoặc 'Total', bỏ dấu chấm/phẩy phân cách hàng nghìn),
                          "unit": "Đơn vị tính (từ cột 'ĐVT' hoặc 'Unit', ví dụ: 'Cái', 'Kg', 'Thùng')",
                          "warehouse": "Tên kho hàng (từ cột 'Kho nhập', ví dụ: 'Kho 1 (KH001)' hoặc 'Kho 2 (KH002)', đọc chính xác cả tên và mã trong ngoặc)"
                        }
                      ],
                      "totalAmount": Tổng tiền (số thực, từ 'Tổng' hoặc 'Total', bỏ dấu chấm/phẩy phân cách hàng nghìn, nếu có)
                    }

                    Lưu ý:
                    - Chỉ trả về JSON, không thêm text khác
                    - Nếu không tìm thấy thông tin, để giá trị null hoặc rỗng
                    - Đảm bảo tất cả số liệu là chính xác
                    - Tên sản phẩm phải rõ ràng, không viết tắt
                    - Với số tiền, bỏ dấu chấm/phẩy phân cách hàng nghìn (ví dụ: "1.850.000" -> 1850000)
                    - Nếu ảnh là form web đã điền, đọc trực tiếp từ các field và table
                    - QUAN TRỌNG: Đọc chính xác tên kho hàng từ cột 'Kho nhập', bao gồm cả mã trong ngoặc (ví dụ: "Kho 1 (KH001)")
                    - RẤT QUAN TRỌNG: Khi đọc tên nhà cung cấp/khách hàng, phải đọc ĐẦY ĐỦ toàn bộ tên, không được bỏ sót bất kỳ từ nào. Ví dụ: "Đỗ Quốc Huy" phải đọc đầy đủ, không được chỉ đọc "Huy" hoặc "Quốc Huy"
                    - Khi đọc tên, phải đọc từ đầu đến cuối, bao gồm cả họ, tên đệm và tên. Không được cắt ngắn hoặc chỉ lấy phần cuối
                    """;
        } else {
            return """
                    Bạn là chuyên gia OCR và xử lý phiếu xuất kho.
                    Hãy đọc ảnh (có thể là phiếu xuất kho thật, form web, hoặc screenshot) và trích xuất thông tin sau đây, trả về dưới dạng JSON:

                    {
                      "customerName": "Tên khách hàng ĐẦY ĐỦ (tìm trong 'Khách hàng', 'Customer', 'Tên khách hàng', hoặc 'Nguồn xuất'). QUAN TRỌNG: Đọc đầy đủ tên từ đầu đến cuối, bao gồm cả họ, tên đệm và tên. KHÔNG được cắt ngắn, KHÔNG được chỉ lấy phần cuối. Ví dụ: Nếu trong ảnh có 'Đỗ Quốc Huy' thì phải trả về 'Đỗ Quốc Huy', KHÔNG được chỉ trả về 'Huy' hoặc 'Quốc Huy'. Phải đọc toàn bộ text trong field đó",
                      "customerPhone": "Số điện thoại khách hàng (tìm trong 'Số điện thoại', 'Phone', hoặc 'SĐT')",
                      "customerAddress": "Địa chỉ khách hàng ĐẦY ĐỦ (tìm trong 'Địa chỉ', 'Address'). QUAN TRỌNG: Đọc đầy đủ địa chỉ, không bỏ sót",
                      "receiptCode": "Mã phiếu xuất (nếu có, bỏ qua nếu là 'Tự động tạo')",
                      "receiptDate": "Ngày phiếu (nếu có)",
                      "note": "Lý do xuất hoặc ghi chú (tìm trong 'Lý do xuất', 'Ghi chú', hoặc 'Note')",
                      "products": [
                        {
                          "name": "Tên sản phẩm (từ cột 'Tên hàng hóa' hoặc 'Product Name')",
                          "code": "Mã sản phẩm (từ cột 'Mã hàng' hoặc 'Product Code')",
                          "quantity": Số lượng (số nguyên, từ cột 'SL' hoặc 'Quantity'),
                          "unitPrice": Đơn giá (số thực, từ cột 'Đơn giá' hoặc 'Unit Price', bỏ dấu chấm/phẩy phân cách hàng nghìn),
                          "discount": Chiết khấu phần trăm (số thực, từ cột 'Chiết khấu (%)' hoặc 'Discount', nếu có),
                          "totalPrice": Thành tiền (số thực, từ cột 'Thành tiền' hoặc 'Total', bỏ dấu chấm/phẩy phân cách hàng nghìn),
                          "unit": "Đơn vị tính (từ cột 'ĐVT' hoặc 'Unit', ví dụ: 'Cái', 'Kg', 'Thùng')",
                          "warehouse": "Tên kho hàng (từ cột 'Kho xuất', ví dụ: 'Kho 1 (KH001)' hoặc 'Kho 2 (KH002)', đọc chính xác cả tên và mã trong ngoặc)"
                        }
                      ],
                      "totalAmount": Tổng tiền (số thực, từ 'Tổng' hoặc 'Total', bỏ dấu chấm/phẩy phân cách hàng nghìn, nếu có)
                    }

                    Lưu ý:
                    - Chỉ trả về JSON, không thêm text khác
                    - Nếu không tìm thấy thông tin, để giá trị null hoặc rỗng
                    - Đảm bảo tất cả số liệu là chính xác
                    - Tên sản phẩm phải rõ ràng, không viết tắt
                    - Với số tiền, bỏ dấu chấm/phẩy phân cách hàng nghìn (ví dụ: "1.850.000" -> 1850000)
                    - Nếu ảnh là form web đã điền, đọc trực tiếp từ các field và table
                    - QUAN TRỌNG: Đọc chính xác tên kho hàng từ cột 'Kho xuất', bao gồm cả mã trong ngoặc (ví dụ: "Kho 1 (KH001)")
                    - RẤT QUAN TRỌNG: Khi đọc tên khách hàng, phải đọc ĐẦY ĐỦ toàn bộ tên từ đầu đến cuối, bao gồm cả họ, tên đệm và tên. Không được cắt ngắn hoặc chỉ lấy phần cuối. Ví dụ: "Đỗ Quốc Huy" phải đọc đầy đủ là "Đỗ Quốc Huy", KHÔNG được chỉ đọc "Huy" hoặc "Quốc Huy"
                    - Khi đọc tên, hãy đọc toàn bộ text trong field "Tên khách hàng" hoặc "Khách hàng", không được bỏ sót bất kỳ từ nào
                    """;
        }
    }

    private ReceiptOCRResponse parseGeminiResponse(String geminiText, String receiptType) {
        try {
            // Tìm JSON trong response (có thể có text thêm)
            String jsonText = extractJsonFromText(geminiText);

            JsonNode root = objectMapper.readTree(jsonText);

            ReceiptOCRResponse response = new ReceiptOCRResponse();
            response.setReceiptType(receiptType);

            // Parse thông tin chung
            if ("IMPORT".equals(receiptType)) {
                response.setSupplierName(getTextValue(root, "supplierName"));
                response.setSupplierPhone(getTextValue(root, "supplierPhone"));
                response.setSupplierAddress(getTextValue(root, "supplierAddress"));
            } else {
                response.setCustomerName(getTextValue(root, "customerName"));
                response.setCustomerPhone(getTextValue(root, "customerPhone"));
                response.setCustomerAddress(getTextValue(root, "customerAddress"));
            }

            response.setReceiptCode(getTextValue(root, "receiptCode"));
            response.setReceiptDate(getTextValue(root, "receiptDate"));
            response.setNote(getTextValue(root, "note"));
            response.setTotalAmount(getDoubleValue(root, "totalAmount"));
            response.setRawText(geminiText);
            response.setConfidence(0.9); // Có thể tính toán dựa trên độ tin cậy thực tế

            // Parse danh sách sản phẩm
            List<ReceiptOCRResponse.ExtractedProduct> products = new ArrayList<>();
            if (root.has("products") && root.get("products").isArray()) {
                for (JsonNode productNode : root.get("products")) {
                    ReceiptOCRResponse.ExtractedProduct product = new ReceiptOCRResponse.ExtractedProduct();
                    product.setName(getTextValue(productNode, "name"));
                    product.setCode(getTextValue(productNode, "code"));
                    product.setQuantity(getIntValue(productNode, "quantity"));
                    product.setUnitPrice(getDoubleValue(productNode, "unitPrice"));
                    product.setDiscount(getDoubleValue(productNode, "discount"));
                    product.setTotalPrice(getDoubleValue(productNode, "totalPrice"));
                    product.setUnit(getTextValue(productNode, "unit"));
                    product.setWarehouse(getTextValue(productNode, "warehouse")); // Parse warehouse từ AI
                    products.add(product);
                }
            }
            response.setProducts(products);

            return response;
        } catch (Exception e) {
            log.error("Error parsing Gemini response", e);
            throw new RuntimeException("Không thể parse dữ liệu từ AI: " + e.getMessage(), e);
        }
    }

    private String extractJsonFromText(String text) {
        // Tìm JSON object trong text
        int startIdx = text.indexOf("{");
        int endIdx = text.lastIndexOf("}");

        if (startIdx >= 0 && endIdx > startIdx) {
            return text.substring(startIdx, endIdx + 1);
        }

        // Nếu không tìm thấy, trả về toàn bộ text
        return text;
    }

    private String getTextValue(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText("");
        }
        return null;
    }

    private Integer getIntValue(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asInt(0);
        }
        return null;
    }

    private Double getDoubleValue(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asDouble(0.0);
        }
        return null;
    }

    /**
     * Tạo text để search từ response (dùng để tạo embedding)
     */
    private String buildSearchText(ReceiptOCRResponse response) {
        StringBuilder text = new StringBuilder();

        if ("IMPORT".equals(response.getReceiptType())) {
            if (response.getSupplierName() != null) {
                text.append("Nhà cung cấp: ").append(response.getSupplierName()).append(" ");
            }
        } else {
            if (response.getCustomerName() != null) {
                text.append("Khách hàng: ").append(response.getCustomerName()).append(" ");
            }
        }

        if (response.getProducts() != null) {
            for (ReceiptOCRResponse.ExtractedProduct product : response.getProducts()) {
                if (product.getName() != null) {
                    text.append("Sản phẩm: ").append(product.getName()).append(" ");
                }
                if (product.getCode() != null) {
                    text.append("Mã: ").append(product.getCode()).append(" ");
                }
            }
        }

        return text.toString().trim();
    }

    /**
     * Tìm kiếm vector trong Milvus và điền thông tin vào response
     */
    private void enrichWithVectorSearch(ReceiptOCRResponse response, List<Float> embedding, String receiptType) {
        try {
            // Tìm kiếm top 5 phiếu tương tự nhất
            List<Map<String, Object>> similarReceipts = milvusService.searchSimilar(embedding, 5);

            if (similarReceipts.isEmpty()) {
                log.info("No similar receipts found in Milvus");
                return;
            }

            log.info("Found {} similar receipts", similarReceipts.size());

            // Lấy phiếu tương tự nhất (score thấp nhất = tương tự nhất với L2 distance)
            Map<String, Object> mostSimilar = similarReceipts.get(0);
            Double score = (Double) mostSimilar.get("score");

            // Chỉ sử dụng nếu score < 1.0 (tương tự đủ cao)
            if (score != null && score < 1.0) {
                log.info("Using similar receipt with score: {}", score);

                // Parse metadata từ phiếu tương tự
                String metadataStr = (String) mostSimilar.get("metadata");
                if (metadataStr != null && !metadataStr.isEmpty()) {
                    try {
                        JsonNode metadata = objectMapper.readTree(metadataStr);

                        // Điền thông tin supplier/customer nếu chưa có
                        if ("IMPORT".equals(receiptType)) {
                            if (response.getSupplierName() == null && metadata.has("supplierName")) {
                                response.setSupplierName(metadata.get("supplierName").asText());
                            }
                            if (response.getSupplierPhone() == null && metadata.has("supplierPhone")) {
                                response.setSupplierPhone(metadata.get("supplierPhone").asText());
                            }
                            if (response.getSupplierAddress() == null && metadata.has("supplierAddress")) {
                                response.setSupplierAddress(metadata.get("supplierAddress").asText());
                            }
                        } else {
                            if (response.getCustomerName() == null && metadata.has("customerName")) {
                                response.setCustomerName(metadata.get("customerName").asText());
                            }
                            if (response.getCustomerPhone() == null && metadata.has("customerPhone")) {
                                response.setCustomerPhone(metadata.get("customerPhone").asText());
                            }
                            if (response.getCustomerAddress() == null && metadata.has("customerAddress")) {
                                response.setCustomerAddress(metadata.get("customerAddress").asText());
                            }
                        }

                        // Mapping sản phẩm: nếu có thông tin sản phẩm tương tự, có thể gợi ý productId
                        if (metadata.has("products") && response.getProducts() != null) {
                            JsonNode similarProducts = metadata.get("products");
                            for (ReceiptOCRResponse.ExtractedProduct product : response.getProducts()) {
                                // Tìm sản phẩm tương tự trong metadata
                                if (product.getName() != null && similarProducts.isArray()) {
                                    for (JsonNode similarProduct : similarProducts) {
                                        String similarName = similarProduct.has("name")
                                                ? similarProduct.get("name").asText()
                                                : "";
                                        if (product.getName().equalsIgnoreCase(similarName)
                                                || product.getName().toLowerCase().contains(similarName.toLowerCase())
                                                || similarName.toLowerCase()
                                                        .contains(product.getName().toLowerCase())) {
                                            // Lưu productId nếu có trong metadata
                                            if (similarProduct.has("productId")) {
                                                product.setSuggestedProductId(similarProduct.get("productId").asLong());
                                                product.setMatchScore(score);
                                                log.debug("Found similar product: {} -> productId: {}, score: {}",
                                                        product.getName(), product.getSuggestedProductId(), score);
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse metadata from similar receipt", e);
                    }
                }
            } else {
                log.info("Similar receipt score too high ({}), not using it", score);
            }
        } catch (Exception e) {
            log.warn("Failed to enrich with vector search", e);
            // Không throw exception, chỉ log warning
        }
    }

    /**
     * Lưu embedding và metadata vào Milvus
     */
    private void saveToMilvus(ReceiptOCRResponse response, List<Float> embedding, String rawText) {
        try {
            // Tạo metadata JSON
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("receiptType", response.getReceiptType());

            if ("IMPORT".equals(response.getReceiptType())) {
                metadata.put("supplierName", response.getSupplierName());
                metadata.put("supplierPhone", response.getSupplierPhone());
                metadata.put("supplierAddress", response.getSupplierAddress());
            } else {
                metadata.put("customerName", response.getCustomerName());
                metadata.put("customerPhone", response.getCustomerPhone());
                metadata.put("customerAddress", response.getCustomerAddress());
            }

            metadata.put("receiptCode", response.getReceiptCode());
            metadata.put("receiptDate", response.getReceiptDate());
            metadata.put("note", response.getNote());
            metadata.put("totalAmount", response.getTotalAmount());

            // Lưu thông tin sản phẩm
            if (response.getProducts() != null) {
                List<Map<String, Object>> productsMetadata = new ArrayList<>();
                for (ReceiptOCRResponse.ExtractedProduct product : response.getProducts()) {
                    Map<String, Object> productMeta = new HashMap<>();
                    productMeta.put("name", product.getName());
                    productMeta.put("code", product.getCode());
                    productMeta.put("quantity", product.getQuantity());
                    productMeta.put("unitPrice", product.getUnitPrice());
                    productMeta.put("unit", product.getUnit());
                    productsMetadata.add(productMeta);
                }
                metadata.put("products", productsMetadata);
            }

            // Lưu vào Milvus
            milvusService.saveEmbedding(
                    response.getReceiptType(),
                    response.getSupplierName() != null ? response.getSupplierName() : "",
                    response.getCustomerName() != null ? response.getCustomerName() : "",
                    embedding,
                    metadata);

            log.info("Saved receipt embedding to Milvus");
        } catch (Exception e) {
            log.warn("Failed to save to Milvus", e);
            // Không throw exception, chỉ log warning
        }
    }
}
