package com.example.aiservice.service;

import com.example.aiservice.dto.ProductOCRRequest;
import com.example.aiservice.dto.ProductOCRResponse;
import com.example.aiservice.dto.ReceiptOCRRequest;
import com.example.aiservice.dto.ReceiptOCRResponse;
import com.example.aiservice.dto.UpdateReceiptMetadataRequest;
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

    /**
     * Xử lý batch nhiều ảnh: merge products + metadata.
     */
    public ReceiptOCRResponse processBatchReceiptImages(ReceiptOCRRequest request) {
        List<ReceiptOCRResponse.ExtractedProduct> mergedProducts = new ArrayList<>();
        ReceiptOCRResponse baseResponse = new ReceiptOCRResponse();
        baseResponse.setReceiptType(request.getReceiptType());

        List<String> imageSources = new ArrayList<>();
        if (request.getImageUrls() != null) {
            imageSources.addAll(request.getImageUrls());
        }
        if (request.getImageBase64s() != null) {
            imageSources.addAll(request.getImageBase64s());
        }

        double totalAmount = 0.0;

        for (String source : imageSources) {
            ReceiptOCRRequest single = new ReceiptOCRRequest();
            single.setReceiptType(request.getReceiptType());
            if (source != null && source.startsWith("http")) {
                single.setImageUrl(source);
            } else {
                single.setImageBase64(source);
            }

            ReceiptOCRResponse resp = processReceiptImage(single);

            if (resp.getProducts() != null) {
                for (ReceiptOCRResponse.ExtractedProduct p : resp.getProducts()) {
                    // dedupe theo name+code
                    boolean exists = mergedProducts.stream().anyMatch(mp -> Objects.equals(mp.getName(), p.getName()) &&
                            Objects.equals(mp.getCode(), p.getCode()));
                    if (!exists) {
                        mergedProducts.add(p);
                    }
                }
            }

            if (resp.getTotalAmount() != null) {
                totalAmount += resp.getTotalAmount();
            }

            // Merge thông tin supplier/customer nếu thiếu
            if (baseResponse.getSupplierName() == null)
                baseResponse.setSupplierName(resp.getSupplierName());
            if (baseResponse.getSupplierPhone() == null)
                baseResponse.setSupplierPhone(resp.getSupplierPhone());
            if (baseResponse.getSupplierAddress() == null)
                baseResponse.setSupplierAddress(resp.getSupplierAddress());
            if (baseResponse.getCustomerName() == null)
                baseResponse.setCustomerName(resp.getCustomerName());
            if (baseResponse.getCustomerPhone() == null)
                baseResponse.setCustomerPhone(resp.getCustomerPhone());
            if (baseResponse.getCustomerAddress() == null)
                baseResponse.setCustomerAddress(resp.getCustomerAddress());
            if (baseResponse.getReceiptCode() == null)
                baseResponse.setReceiptCode(resp.getReceiptCode());
            if (baseResponse.getReceiptDate() == null)
                baseResponse.setReceiptDate(resp.getReceiptDate());
            if (baseResponse.getNote() == null)
                baseResponse.setNote(resp.getNote());
            if (baseResponse.getRawText() == null)
                baseResponse.setRawText(resp.getRawText());
        }

        baseResponse.setProducts(mergedProducts);
        baseResponse.setTotalAmount(totalAmount > 0 ? totalAmount : null);
        return baseResponse;
    }

    /**
     * Cập nhật/ghi thêm metadata đã được user chỉnh sửa vào Milvus
     * (học từ thao tác mapping thủ công).
     */
    public void updateReceiptMetadata(UpdateReceiptMetadataRequest request) {
        try {
            String searchText = buildSearchText(request);
            List<Float> embedding = embeddingService.generateEmbedding(searchText);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("receiptType", request.getReceiptType());
            metadata.put("supplierName", request.getSupplierName());
            metadata.put("customerName", request.getCustomerName());
            metadata.put("receiptCode", request.getReceiptCode());
            metadata.put("receiptDate", request.getReceiptDate());
            metadata.put("note", request.getNote());
            metadata.put("totalAmount", request.getTotalAmount());

            if (request.getProducts() != null && !request.getProducts().isEmpty()) {
                List<Map<String, Object>> productsMeta = new ArrayList<>();
                for (UpdateReceiptMetadataRequest.ProductMetadata p : request.getProducts()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("productId", p.getProductId());
                    m.put("name", p.getName());
                    m.put("code", p.getCode());
                    m.put("quantity", p.getQuantity());
                    m.put("unitPrice", p.getUnitPrice());
                    m.put("totalPrice", p.getTotalPrice());
                    m.put("unit", p.getUnit());
                    m.put("warehouse", p.getWarehouse());
                    productsMeta.add(m);
                }
                metadata.put("products", productsMeta);
            }

            milvusService.saveEmbedding(
                    request.getReceiptType(),
                    request.getSupplierName() != null ? request.getSupplierName() : "",
                    request.getCustomerName() != null ? request.getCustomerName() : "",
                    embedding,
                    metadata);

            log.info("Updated receipt metadata to Milvus");
        } catch (Exception e) {
            log.warn("Failed to update receipt metadata", e);
        }
    }

    /**
     * Đọc ảnh sản phẩm và trích xuất thông tin
     */
    public ProductOCRResponse processProductImage(ProductOCRRequest request) {
        try {
            // Bước 1: Lấy ảnh từ URL hoặc base64
            String imageData = getProductImageData(request);

            // Bước 2: Gọi Gemini để đọc ảnh và trích xuất thông tin
            String extractedData = extractProductDataFromImage(imageData);

            // Bước 3: Parse JSON response từ Gemini
            ProductOCRResponse response = parseProductOCRResponse(extractedData);

            return response;
        } catch (Exception e) {
            log.error("Error processing product image", e);
            throw new RuntimeException("Không thể xử lý ảnh sản phẩm: " + e.getMessage(), e);
        }
    }

    private String getProductImageData(ProductOCRRequest request) {
        if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
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
            String base64 = request.getImageBase64();
            if (base64.contains(",")) {
                base64 = base64.substring(base64.indexOf(",") + 1);
            }
            return base64;
        }

        throw new IllegalArgumentException("Cần có imageUrl hoặc imageBase64");
    }

    private String extractProductDataFromImage(String imageBase64) {
        String prompt = buildProductPrompt();

        Map<String, Object> body = new HashMap<>();
        List<Map<String, Object>> parts = new ArrayList<>();

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", prompt);
        parts.add(textPart);

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
            if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new RuntimeException("Đã vượt quá hạn mức sử dụng Gemini API. Vui lòng thử lại sau.");
            }
            throw new RuntimeException("Gemini API error: " + ex.getStatusCode());
        } catch (Exception ex) {
            log.error("Gemini invocation failed", ex);
            throw new RuntimeException("Không thể kết nối Gemini: " + ex.getMessage());
        }
    }

    private String buildProductPrompt() {
        return """
                Bạn là chuyên gia OCR và xử lý ảnh sản phẩm.
                Hãy đọc ảnh sản phẩm (có thể là ảnh sản phẩm thật, nhãn mác, bao bì, thông tin sản phẩm, hoặc danh sách sản phẩm) và trích xuất thông tin sau đây, trả về dưới dạng JSON:

                QUAN TRỌNG:
                - Nếu ảnh chỉ có MỘT sản phẩm, trả về object đơn giản với các trường ở dưới
                - Nếu ảnh có NHIỀU sản phẩm (ví dụ: danh sách sản phẩm, bảng giá, catalog), trả về mảng "products" với mỗi phần tử là một sản phẩm

                Cấu trúc JSON cho MỘT sản phẩm:
                {
                  "name": "Tên sản phẩm (đọc từ nhãn mác, bao bì, hoặc thông tin trong ảnh). Đọc đầy đủ tên sản phẩm, không viết tắt.",
                  "code": "Mã sản phẩm (nếu có, từ mã vạch, mã SKU, hoặc mã sản phẩm trên nhãn). RẤT QUAN TRỌNG: Đọc chính xác từng ký tự, loại bỏ khoảng trắng thừa giữa các ký tự. Ví dụ: Nếu trong ảnh có 'SPDT 002' thì phải trả về 'SPDT002' (không có khoảng trắng). Phân biệt rõ số 0 và chữ O, số 1 và chữ I.",
                  "price": Giá sản phẩm (số thực, nếu có trong ảnh, bỏ dấu chấm/phẩy phân cách hàng nghìn). Nếu không có giá, để null,
                  "description": "Mô tả sản phẩm (nếu có trong ảnh, đọc từ phần mô tả, thông tin sản phẩm, hoặc đặc điểm nổi bật)",
                  "category": "Tên danh mục/nhóm hàng (nếu có trong ảnh, ví dụ: 'Điện thoại', 'Laptop', 'Quần áo', v.v.)",
                  "unit": "Đơn vị tính (nếu có trong ảnh, ví dụ: 'Cái', 'Kg', 'Thùng', 'Hộp')",
                  "brand": "Thương hiệu (nếu có trong ảnh, ví dụ: 'Apple', 'Samsung', 'Nike')",
                  "specifications": "Thông số kỹ thuật (nếu có trong ảnh, đọc từ phần thông số, đặc điểm kỹ thuật, hoặc chi tiết sản phẩm)",
                  "supplier": "Tên nhà cung cấp (nếu có trong ảnh, đọc từ phần 'Nhà cung cấp', 'Supplier', 'NCC', hoặc thông tin nhà cung cấp)",
                  "warehouse": "Tên kho hàng (nếu có trong ảnh, đọc từ phần 'Kho hàng', 'Warehouse', 'Kho', hoặc thông tin kho)",
                  "nameConfidence": Độ tin cậy khi đọc tên (0-1),
                  "codeConfidence": Độ tin cậy khi đọc mã (0-1),
                  "priceConfidence": Độ tin cậy khi đọc giá (0-1)
                }

                Cấu trúc JSON cho NHIỀU sản phẩm:
                {
                  "products": [
                    {
                      "name": "Tên sản phẩm 1",
                      "code": "Mã sản phẩm 1",
                      "price": Giá sản phẩm 1,
                      "description": "Mô tả sản phẩm 1",
                      "category": "Danh mục 1",
                      "unit": "Đơn vị 1",
                      "brand": "Thương hiệu 1",
                      "specifications": "Thông số 1",
                      "supplier": "Nhà cung cấp 1",
                      "warehouse": "Kho hàng 1"
                    },
                    {
                      "name": "Tên sản phẩm 2",
                      "code": "Mã sản phẩm 2",
                      "price": Giá sản phẩm 2,
                      ...
                    }
                  ]
                }

                Ví dụ JSON cho MỘT sản phẩm:
                {
                  "name": "iPhone 15 Pro Max 256GB",
                  "code": "IP15PM256",
                  "price": 28990000,
                  "description": "Điện thoại thông minh cao cấp với chip A17 Pro, camera 48MP, màn hình Super Retina XDR 6.7 inch",
                  "category": "Điện thoại",
                  "unit": "Cái",
                  "brand": "Apple",
                  "specifications": "Chip A17 Pro, RAM 8GB, Bộ nhớ 256GB, Camera 48MP + 12MP + 12MP, Pin 4441mAh",
                  "supplier": "Minh Anh Mobile",
                  "warehouse": "Kho trung tâm Hà Nội",
                  "nameConfidence": 0.95,
                  "codeConfidence": 0.90,
                  "priceConfidence": 0.92
                }

                Lưu ý:
                - Chỉ trả về JSON, không thêm text khác
                - Nếu không tìm thấy thông tin, để giá trị null hoặc rỗng
                - Đảm bảo tất cả số liệu là chính xác
                - Với số tiền, bỏ dấu chấm/phẩy phân cách hàng nghìn (ví dụ: "28.990.000" -> 28990000)
                - Đọc đầy đủ thông tin, không viết tắt
                - Nếu ảnh có nhiều sản phẩm, PHẢI trả về mảng "products", không được chỉ trả về một sản phẩm
                """;
    }

    private ProductOCRResponse parseProductOCRResponse(String extractedData) {
        try {
            // Tìm JSON trong response (có thể có text thêm)
            String jsonStr = extractedData.trim();

            // Tìm JSON object đầu tiên
            int startIdx = jsonStr.indexOf("{");
            int endIdx = jsonStr.lastIndexOf("}");

            if (startIdx >= 0 && endIdx > startIdx) {
                jsonStr = jsonStr.substring(startIdx, endIdx + 1);
            }

            JsonNode jsonNode = objectMapper.readTree(jsonStr);
            ProductOCRResponse response = new ProductOCRResponse();

            // Kiểm tra xem có mảng products không (nhiều sản phẩm)
            if (jsonNode.has("products") && jsonNode.get("products").isArray()) {
                // Xử lý nhiều sản phẩm
                List<ProductOCRResponse.ProductItem> products = new ArrayList<>();
                JsonNode productsArray = jsonNode.get("products");

                for (JsonNode productNode : productsArray) {
                    ProductOCRResponse.ProductItem item = new ProductOCRResponse.ProductItem();
                    item.setName(productNode.has("name") ? productNode.get("name").asText(null) : null);
                    item.setCode(productNode.has("code") ? productNode.get("code").asText(null) : null);
                    item.setPrice(productNode.has("price") && !productNode.get("price").isNull()
                            ? productNode.get("price").asDouble()
                            : null);
                    item.setDescription(
                            productNode.has("description") ? productNode.get("description").asText(null) : null);
                    item.setCategory(productNode.has("category") ? productNode.get("category").asText(null) : null);
                    item.setUnit(productNode.has("unit") ? productNode.get("unit").asText(null) : null);
                    item.setBrand(productNode.has("brand") ? productNode.get("brand").asText(null) : null);
                    item.setSpecifications(
                            productNode.has("specifications") ? productNode.get("specifications").asText(null) : null);
                    item.setSupplier(productNode.has("supplier") ? productNode.get("supplier").asText(null) : null);
                    item.setWarehouse(productNode.has("warehouse") ? productNode.get("warehouse").asText(null) : null);
                    products.add(item);
                }
                response.setProducts(products);

                // Lấy sản phẩm đầu tiên làm thông tin chính (nếu có)
                if (!products.isEmpty()) {
                    ProductOCRResponse.ProductItem first = products.get(0);
                    response.setName(first.getName());
                    response.setCode(first.getCode());
                    response.setPrice(first.getPrice());
                    response.setDescription(first.getDescription());
                    response.setCategory(first.getCategory());
                    response.setUnit(first.getUnit());
                    response.setBrand(first.getBrand());
                    response.setSpecifications(first.getSpecifications());
                    response.setSupplier(first.getSupplier());
                    response.setWarehouse(first.getWarehouse());
                }
            } else {
                // Xử lý một sản phẩm
                response.setName(jsonNode.has("name") ? jsonNode.get("name").asText(null) : null);
                response.setCode(jsonNode.has("code") ? jsonNode.get("code").asText(null) : null);
                response.setPrice(
                        jsonNode.has("price") && !jsonNode.get("price").isNull() ? jsonNode.get("price").asDouble()
                                : null);
                response.setDescription(jsonNode.has("description") ? jsonNode.get("description").asText(null) : null);
                response.setCategory(jsonNode.has("category") ? jsonNode.get("category").asText(null) : null);
                response.setUnit(jsonNode.has("unit") ? jsonNode.get("unit").asText(null) : null);
                response.setBrand(jsonNode.has("brand") ? jsonNode.get("brand").asText(null) : null);
                response.setSpecifications(
                        jsonNode.has("specifications") ? jsonNode.get("specifications").asText(null) : null);
                response.setSupplier(jsonNode.has("supplier") ? jsonNode.get("supplier").asText(null) : null);
                response.setWarehouse(jsonNode.has("warehouse") ? jsonNode.get("warehouse").asText(null) : null);
            }

            response.setRawText(extractedData);

            // Confidence scores
            if (jsonNode.has("nameConfidence")) {
                response.setNameConfidence(jsonNode.get("nameConfidence").asDouble());
            }
            if (jsonNode.has("codeConfidence")) {
                response.setCodeConfidence(jsonNode.get("codeConfidence").asDouble());
            }
            if (jsonNode.has("priceConfidence")) {
                response.setPriceConfidence(jsonNode.get("priceConfidence").asDouble());
            }

            // Tính confidence tổng thể
            double totalConfidence = 0.0;
            int count = 0;
            if (response.getNameConfidence() != null) {
                totalConfidence += response.getNameConfidence();
                count++;
            }
            if (response.getCodeConfidence() != null) {
                totalConfidence += response.getCodeConfidence();
                count++;
            }
            if (response.getPriceConfidence() != null) {
                totalConfidence += response.getPriceConfidence();
                count++;
            }
            response.setConfidence(count > 0 ? totalConfidence / count : null);

            return response;
        } catch (Exception e) {
            log.error("Failed to parse product OCR response", e);
            throw new RuntimeException("Không thể parse kết quả OCR: " + e.getMessage(), e);
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
                                "(https://aistudio.google.com/usage) hoặc đợi đến ngày mai để quota reset.");
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

                    QUAN TRỌNG VỀ CÁCH ĐỌC BẢNG SẢN PHẨM:
                    - Bảng sản phẩm có cấu trúc: Mỗi HÀNG NGANG (row) là một sản phẩm, mỗi CỘT DỌC là một loại thông tin
                    - Đọc từng HÀNG NGANG một cách tuần tự, từ trên xuống dưới, bắt đầu từ hàng đầu tiên (sau header)
                    - Mỗi hàng là một sản phẩm độc lập với thông tin riêng của nó
                    - Với mỗi hàng, đọc TẤT CẢ các cột của hàng đó: STT, Tên hàng hóa, Mã hàng, ĐVT, Kho nhập, Tồn kho, Đơn giá, SL, Chiết khấu, Thành tiền
                    - Đặc biệt chú ý: Cột "Kho nhập" của mỗi hàng có thể KHÁC NHAU hoàn toàn. Hàng 1 có thể là "kho trung tâm Hà Nội", hàng 2 có thể là "kho hồ Chí Minh", hàng 3 lại là "Kho trung tâm Hà Nội"
                    - KHÔNG được giả định tất cả hàng đều có cùng kho hàng
                    - KHÔNG được copy giá trị "Kho nhập" từ hàng trước
                    - KHÔNG được đọc theo cột dọc (đọc tất cả giá trị trong cột "Kho nhập" cùng lúc)
                    - PHẢI đọc từng hàng một, với mỗi hàng thì đọc giá trị "Kho nhập" của hàng đó
                    - Phải đọc chính xác text trong ô "Kho nhập" của TỪNG HÀNG, không được nhìn sang hàng khác

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
                          "code": "Mã sản phẩm (từ cột 'Mã hàng' hoặc 'Product Code'). RẤT QUAN TRỌNG: Đọc chính xác từng ký tự, loại bỏ khoảng trắng thừa giữa các ký tự. Ví dụ: Nếu trong ảnh có 'SPDT 002' thì phải trả về 'SPDT002' (không có khoảng trắng), hoặc nếu có 'SPDT002' thì giữ nguyên 'SPDT002'. Phân biệt rõ số 0 và chữ O, số 1 và chữ I. Mã hàng thường không có khoảng trắng giữa các ký tự.",
                          "quantity": Số lượng (số nguyên, từ cột 'SL' hoặc 'Quantity'),
                          "unitPrice": Đơn giá GỐC (số thực, từ cột 'Đơn giá' hoặc 'Unit Price', bỏ dấu chấm/phẩy phân cách hàng nghìn). RẤT QUAN TRỌNG: Đây phải là giá GỐC TRƯỚC KHI CHIẾT KHẤU. Nếu trong ảnh đơn giá đã là giá sau chiết khấu, hãy tính ngược lại: unitPrice = totalPrice / quantity / (1 - discount/100). Ví dụ: Nếu Thành tiền = 260.910.000, SL = 10, Chiết khấu = 10%, thì unitPrice = 260.910.000 / 10 / 0.9 = 28.990.000 (KHÔNG phải 26.091.000),
                          "discount": Chiết khấu phần trăm (số thực, từ cột 'Chiết khấu (%)' hoặc 'Discount', nếu có),
                          "totalPrice": Thành tiền (số thực, từ cột 'Thành tiền' hoặc 'Total', bỏ dấu chấm/phẩy phân cách hàng nghìn). Đây là giá SAU KHI CHIẾT KHẤU (totalPrice = unitPrice * quantity * (1 - discount/100)),
                          "unit": "Đơn vị tính (từ cột 'ĐVT' hoặc 'Unit', ví dụ: 'Cái', 'Kg', 'Thùng')",
                          "warehouse": "Tên kho hàng (từ cột 'Kho nhập' của TỪNG DÒNG SẢN PHẨM, ví dụ: 'kho trung tâm Hà Nội', 'kho hồ Chí Minh', 'Kho Trung tâm Hà Nội', 'Kho 1 (KH001)' hoặc 'Kho 2 (KH002)'). CỰC KỲ QUAN TRỌNG: Mỗi dòng sản phẩm có thể có kho hàng HOÀN TOÀN KHÁC NHAU. BẮT BUỘC phải đọc chính xác kho hàng từ cột 'Kho nhập' của TỪNG DÒNG RIÊNG BIỆT, TUẦN TỰ theo STT. KHÔNG ĐƯỢC copy kho hàng từ dòng trước, KHÔNG ĐƯỢC giả định tất cả đều cùng một kho. Ví dụ cụ thể: Nếu STT 1 có 'kho trung tâm Hà Nội', STT 2 có 'kho hồ Chí Minh', thì phải trả về chính xác như vậy - STT 2 phải là 'kho hồ Chí Minh' chứ KHÔNG PHẢI 'kho trung tâm Hà Nội'. Đọc từng dòng một cách độc lập, xem xét kỹ từng ô trong cột 'Kho nhập' của từng dòng."
                        }
                      ],
                      "totalAmount": Tổng tiền (số thực, từ 'Tổng' hoặc 'Total', bỏ dấu chấm/phẩy phân cách hàng nghìn, nếu có)
                    }

                    Ví dụ JSON (tuân thủ đúng key, số không dùng dấu ngăn cách nghìn):
                    {
                      "supplierName": "Công ty ABC",
                      "supplierPhone": "0909123456",
                      "supplierAddress": "123 Đường A, Quận 1, TP.HCM",
                      "receiptCode": "PN001",
                      "receiptDate": "2025-01-10",
                      "note": "Nhập hàng tháng 1",
                      "products": [
                        {
                          "name": "Sữa tươi Vinamilk 1L",
                          "code": "SUA1L",
                          "quantity": 10,
                          "unitPrice": 32000,
                          "discount": 0,
                          "totalPrice": 320000,
                          "unit": "Hộp",
                          "warehouse": "Kho trung tâm Hà Nội",
                          "nameConfidence": 0.92,
                          "codeConfidence": 0.88,
                          "quantityConfidence": 0.95,
                          "unitPriceConfidence": 0.9,
                          "totalPriceConfidence": 0.9
                        },
                        {
                          "name": "Bánh mì",
                          "code": "BANHMI",
                          "quantity": 20,
                          "unitPrice": 15000,
                          "discount": 10,
                          "totalPrice": 270000,
                          "unit": "Cái",
                          "warehouse": "kho hồ Chí Minh",
                          "nameConfidence": 0.9,
                          "codeConfidence": 0.85,
                          "quantityConfidence": 0.95,
                          "unitPriceConfidence": 0.9,
                          "totalPriceConfidence": 0.9
                        }
                      ],
                      "totalAmount": 590000
                    }

                    Lưu ý quan trọng về ví dụ: Trong ví dụ trên, dòng 1 có kho hàng là "Kho trung tâm Hà Nội", dòng 2 có kho hàng là "kho hồ Chí Minh" - đây là hai kho KHÁC NHAU. Mỗi dòng phải đọc chính xác kho hàng từ cột 'Kho nhập' của dòng đó.

                    QUY TRÌNH ĐỌC KHO HÀNG (BẮT BUỘC PHẢI LÀM THEO - ĐỌC TỪNG HÀNG ĐỘC LẬP):

                    BƯỚC 1: Xác định bảng sản phẩm trong ảnh (thường có header: STT, Tên hàng hóa, Mã hàng, ĐVT, Kho nhập, ...)

                    BƯỚC 2: Đọc từng HÀNG NGANG (row) một cách tuần tự, bắt đầu từ hàng đầu tiên (STT = 1)

                    BƯỚC 3: Với mỗi hàng, thực hiện các bước sau:
                      3a. Xác định vị trí của hàng đó trong bảng (ví dụ: hàng thứ 2, STT = 2)
                      3b. Tìm cột "Kho nhập" hoặc "Kho hàng" hoặc "Warehouse"
                      3c. Di chuyển mắt đến ô "Kho nhập" của HÀNG ĐÓ (KHÔNG phải hàng khác)
                      3d. Đọc chính xác text trong ô đó, từ trái sang phải, từ trên xuống dưới
                      3e. Ghi lại text vừa đọc vào biến warehouse cho hàng đó
                      3f. KHÔNG được copy text từ hàng trước
                      3g. KHÔNG được giả định text giống hàng trước

                    BƯỚC 4: Ví dụ cụ thể với STT 2:
                      - Nếu hàng 1 (STT 1) có "kho trung tâm Hà Nội" trong cột "Kho nhập"
                      - Và hàng 2 (STT 2) có "kho hồ Chí Minh" trong cột "Kho nhập"
                      - Thì:
                        * products[0].warehouse = "kho trung tâm Hà Nội" (từ hàng STT 1)
                        * products[1].warehouse = "kho hồ Chí Minh" (từ hàng STT 2, KHÔNG PHẢI "kho trung tâm Hà Nội")
                      - QUAN TRỌNG: Khi đọc hàng STT 2, PHẢI nhìn vào ô "Kho nhập" của hàng STT 2, không được dùng giá trị từ hàng STT 1

                    BƯỚC 5: Đọc đầy đủ tên kho, không viết tắt:
                      - "kho hồ Chí Minh" (ĐÚNG)
                      - "HCM" (SAI - không được viết tắt)
                      - "Hồ Chí Minh" (SAI - thiếu từ "kho")
                      - "kho trung tâm Hà Nội" (ĐÚNG)
                      - "Hà Nội" (SAI - thiếu từ "kho trung tâm")

                    BƯỚC 6: Lặp lại BƯỚC 3 cho TẤT CẢ các hàng trong bảng, mỗi hàng đọc độc lập

                    LƯU Ý QUAN TRỌNG:
                    - Mỗi hàng có thể có kho hàng HOÀN TOÀN KHÁC NHAU
                    - Đọc từng hàng một cách độc lập, không bị ảnh hưởng bởi hàng trước
                    - Nếu trong ảnh có 5 hàng, phải đọc 5 lần, mỗi lần cho 1 hàng
                    - Không được đọc tất cả giá trị trong cột "Kho nhập" cùng lúc rồi copy cho tất cả hàng

                    VÍ DỤ CỤ THỂ VÀ CHI TIẾT:
                    Nếu trong ảnh có bảng:
                    STT | Tên hàng | Mã hàng | Kho nhập | ...
                    1   | iPhone 15| SPDT001 | kho trung tâm Hà Nội | ...
                    2   | iPhone 14| SPDT002 | kho hồ Chí Minh | ...
                    3   | Samsung  | SPDT003 | Kho trung tâm Hà Nội | ...

                    Quá trình đọc:
                    - Hàng 1 (STT 1): Nhìn vào ô "Kho nhập" của hàng 1 → Đọc "kho trung tâm Hà Nội" → Ghi vào products[0].warehouse = "kho trung tâm Hà Nội"
                    - Hàng 2 (STT 2): Nhìn vào ô "Kho nhập" của hàng 2 → Đọc "kho hồ Chí Minh" → Ghi vào products[1].warehouse = "kho hồ Chí Minh" (KHÔNG được copy "kho trung tâm Hà Nội" từ hàng 1)
                    - Hàng 3 (STT 3): Nhìn vào ô "Kho nhập" của hàng 3 → Đọc "Kho trung tâm Hà Nội" → Ghi vào products[2].warehouse = "Kho trung tâm Hà Nội"

                    Kết quả phải trả về:
                    products[0].warehouse = "kho trung tâm Hà Nội"
                    products[1].warehouse = "kho hồ Chí Minh"  (KHÔNG PHẢI "kho trung tâm Hà Nội")
                    products[2].warehouse = "Kho trung tâm Hà Nội"

                    LƯU Ý: Nếu bạn thấy hàng 2 có "kho hồ Chí Minh" trong ảnh nhưng lại trả về "kho trung tâm Hà Nội", đó là SAI. Bạn phải đọc lại hàng 2 một cách cẩn thận.

                    Lưu ý:
                    - Chỉ trả về JSON, không thêm text khác
                    - Nếu không tìm thấy thông tin, để giá trị null hoặc rỗng
                    - Đảm bảo tất cả số liệu là chính xác
                    - Tên sản phẩm phải rõ ràng, không viết tắt
                    - Với số tiền, bỏ dấu chấm/phẩy phân cách hàng nghìn (ví dụ: "1.850.000" -> 1850000)
                    - Nếu ảnh là form web đã điền, đọc trực tiếp từ các field và table
                    - CỰC KỲ QUAN TRỌNG VỀ KHO HÀNG - ĐỌC TỪNG HÀNG ĐỘC LẬP (ĐỌC KỸ PHẦN NÀY):
                      * Mỗi hàng sản phẩm trong bảng có thể có kho hàng HOÀN TOÀN KHÁC NHAU
                      * BẮT BUỘC phải đọc từng HÀNG NGANG một cách tuần tự, từ trên xuống
                      * Với mỗi hàng, tìm cột "Kho nhập" và đọc chính xác text trong ô đó
                      * KHÔNG ĐƯỢC copy kho hàng từ hàng trước
                      * KHÔNG ĐƯỢC giả định tất cả đều cùng một kho
                      * KHÔNG ĐƯỢC bỏ qua việc đọc kho hàng của bất kỳ hàng nào
                      * KHÔNG ĐƯỢC đọc tất cả giá trị trong cột "Kho nhập" cùng lúc rồi copy cho tất cả hàng
                      * Ví dụ cụ thể: Nếu hàng 1 (STT 1) có 'kho trung tâm Hà Nội', hàng 2 (STT 2) có 'kho hồ Chí Minh', hàng 3 (STT 3) có 'Kho trung tâm Hà Nội', thì:
                        - products[0].warehouse = "kho trung tâm Hà Nội"
                        - products[1].warehouse = "kho hồ Chí Minh" (KHÔNG PHẢI "kho trung tâm Hà Nội")
                        - products[2].warehouse = "Kho trung tâm Hà Nội"
                      * Đọc đầy đủ tên kho (ví dụ: 'kho trung tâm Hà Nội', 'kho hồ Chí Minh', không được viết tắt thành 'Hà Nội' hoặc 'HCM')
                      * Nếu có mã trong ngoặc thì đọc cả mã (ví dụ: "Kho 1 (KH001)")
                      * QUAN TRỌNG: Khi đọc hàng STT 2, PHẢI nhìn vào ô "Kho nhập" của hàng STT 2, không được dùng giá trị từ hàng STT 1
                      * QUAN TRỌNG: Đọc theo HÀNG NGANG (row-by-row), không đọc theo cột dọc
                      * QUAN TRỌNG: Nếu bạn thấy trong ảnh hàng STT 2 có "kho hồ Chí Minh" nhưng lại trả về "kho trung tâm Hà Nội", đó là SAI. Hãy đọc lại hàng STT 2 một cách cẩn thận.
                      * QUAN TRỌNG: Trước khi trả về JSON, hãy kiểm tra lại: Mỗi hàng có warehouse riêng của nó chưa? Hàng STT 2 có warehouse khác với hàng STT 1 không (nếu trong ảnh chúng khác nhau)?
                    - RẤT QUAN TRỌNG: Khi đọc tên nhà cung cấp/khách hàng, phải đọc ĐẦY ĐỦ toàn bộ tên, không được bỏ sót bất kỳ từ nào. Ví dụ: "Đỗ Quốc Huy" phải đọc đầy đủ, không được chỉ đọc "Huy" hoặc "Quốc Huy"
                    - Khi đọc tên, phải đọc từ đầu đến cuối, bao gồm cả họ, tên đệm và tên. Không được cắt ngắn hoặc chỉ lấy phần cuối
                    - Trả thêm các trường confidence (0-1) cho từng sản phẩm nếu có: nameConfidence, codeConfidence, quantityConfidence, unitPriceConfidence, totalPriceConfidence
                    - RẤT QUAN TRỌNG VỀ MÃ HÀNG: Khi đọc mã hàng, phải loại bỏ TẤT CẢ khoảng trắng thừa giữa các ký tự. Mã hàng thường là chuỗi liên tục không có khoảng trắng (ví dụ: 'SPDT002' chứ không phải 'SPDT 002'). Nếu trong ảnh có khoảng trắng, hãy tự động loại bỏ để trả về mã hàng chuẩn. Đọc chính xác từng ký tự, phân biệt số 0 và chữ O, số 1 và chữ I.
                    - RẤT QUAN TRỌNG VỀ ĐƠN GIÁ: unitPrice phải là giá GỐC TRƯỚC CHIẾT KHẤU. Nếu trong ảnh cột 'Đơn giá' đã là giá sau chiết khấu, hãy tính ngược lại từ Thành tiền: unitPrice = totalPrice / quantity / (1 - discount/100). Ví dụ: Nếu Thành tiền = 260.910.000, SL = 10, Chiết khấu = 10%, thì unitPrice phải là 28.990.000 (KHÔNG phải 26.091.000). Luôn đảm bảo: unitPrice * quantity * (1 - discount/100) = totalPrice.
                    """;
        } else {
            return """
                    Bạn là chuyên gia OCR và xử lý phiếu xuất kho.
                    Hãy đọc ảnh (có thể là phiếu xuất kho thật, form web, hoặc screenshot) và trích xuất thông tin sau đây, trả về dưới dạng JSON:

                    QUAN TRỌNG VỀ CÁCH ĐỌC BẢNG SẢN PHẨM:
                    - Bảng sản phẩm có cấu trúc: Mỗi HÀNG NGANG (row) là một sản phẩm, mỗi CỘT DỌC là một loại thông tin
                    - Đọc từng HÀNG NGANG một cách tuần tự, từ trên xuống dưới, bắt đầu từ hàng đầu tiên (sau header)
                    - Mỗi hàng là một sản phẩm độc lập với thông tin riêng của nó
                    - Với mỗi hàng, đọc TẤT CẢ các cột của hàng đó: STT, Tên hàng hóa, Mã hàng, ĐVT, Kho xuất, Tồn kho, Đơn giá, SL, Chiết khấu, Thành tiền
                    - Đặc biệt chú ý: Cột "Kho xuất" của mỗi hàng có thể KHÁC NHAU hoàn toàn. Hàng 1 có thể là "kho trung tâm Hà Nội", hàng 2 có thể là "kho hồ Chí Minh", hàng 3 lại là "Kho trung tâm Hà Nội"
                    - KHÔNG được giả định tất cả hàng đều có cùng kho hàng
                    - KHÔNG được copy giá trị "Kho xuất" từ hàng trước
                    - KHÔNG được đọc theo cột dọc (đọc tất cả giá trị trong cột "Kho xuất" cùng lúc)
                    - PHẢI đọc từng hàng một, với mỗi hàng thì đọc giá trị "Kho xuất" của hàng đó
                    - Phải đọc chính xác text trong ô "Kho xuất" của TỪNG HÀNG, không được nhìn sang hàng khác

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
                          "code": "Mã sản phẩm (từ cột 'Mã hàng' hoặc 'Product Code'). RẤT QUAN TRỌNG: Đọc chính xác từng ký tự, loại bỏ khoảng trắng thừa giữa các ký tự. Ví dụ: Nếu trong ảnh có 'SPDT 002' thì phải trả về 'SPDT002' (không có khoảng trắng), hoặc nếu có 'SPDT002' thì giữ nguyên 'SPDT002'. Phân biệt rõ số 0 và chữ O, số 1 và chữ I. Mã hàng thường không có khoảng trắng giữa các ký tự.",
                          "quantity": Số lượng (số nguyên, từ cột 'SL' hoặc 'Quantity'),
                          "unitPrice": Đơn giá GỐC (số thực, từ cột 'Đơn giá' hoặc 'Unit Price', bỏ dấu chấm/phẩy phân cách hàng nghìn). RẤT QUAN TRỌNG: Đây phải là giá GỐC TRƯỚC KHI CHIẾT KHẤU. Nếu trong ảnh đơn giá đã là giá sau chiết khấu, hãy tính ngược lại: unitPrice = totalPrice / quantity / (1 - discount/100). Ví dụ: Nếu Thành tiền = 260.910.000, SL = 10, Chiết khấu = 10%, thì unitPrice = 260.910.000 / 10 / 0.9 = 28.990.000 (KHÔNG phải 26.091.000),
                          "discount": Chiết khấu phần trăm (số thực, từ cột 'Chiết khấu (%)' hoặc 'Discount', nếu có),
                          "totalPrice": Thành tiền (số thực, từ cột 'Thành tiền' hoặc 'Total', bỏ dấu chấm/phẩy phân cách hàng nghìn). Đây là giá SAU KHI CHIẾT KHẤU (totalPrice = unitPrice * quantity * (1 - discount/100)),
                          "unit": "Đơn vị tính (từ cột 'ĐVT' hoặc 'Unit', ví dụ: 'Cái', 'Kg', 'Thùng')",
                          "warehouse": "Tên kho hàng (từ cột 'Kho xuất' của TỪNG DÒNG SẢN PHẨM, ví dụ: 'kho trung tâm Hà Nội', 'kho hồ Chí Minh', 'Kho Trung tâm Hà Nội', 'Kho 1 (KH001)' hoặc 'Kho 2 (KH002)'). CỰC KỲ QUAN TRỌNG: Mỗi dòng sản phẩm có thể có kho hàng HOÀN TOÀN KHÁC NHAU. BẮT BUỘC phải đọc chính xác kho hàng từ cột 'Kho xuất' của TỪNG DÒNG RIÊNG BIỆT, TUẦN TỰ theo STT. KHÔNG ĐƯỢC copy kho hàng từ dòng trước, KHÔNG ĐƯỢC giả định tất cả đều cùng một kho. Ví dụ cụ thể: Nếu STT 1 có 'kho trung tâm Hà Nội', STT 2 có 'kho hồ Chí Minh', thì phải trả về chính xác như vậy - STT 2 phải là 'kho hồ Chí Minh' chứ KHÔNG PHẢI 'kho trung tâm Hà Nội'. Đọc từng dòng một cách độc lập, xem xét kỹ từng ô trong cột 'Kho xuất' của từng dòng."
                        }
                      ],
                      "totalAmount": Tổng tiền (số thực, từ 'Tổng' hoặc 'Total', bỏ dấu chấm/phẩy phân cách hàng nghìn, nếu có)
                    }

                    Ví dụ JSON (tuân thủ đúng key, số không dùng dấu ngăn cách nghìn):
                    {
                      "customerName": "Cửa hàng Minh An",
                      "customerPhone": "0909988776",
                      "customerAddress": "45 Lê Lợi, Đà Nẵng",
                      "receiptCode": "PX001",
                      "receiptDate": "2025-01-12",
                      "note": "Xuất đơn lẻ",
                      "products": [
                        {
                          "name": "Bút bi Thiên Long",
                          "code": "BUT01",
                          "quantity": 20,
                          "unitPrice": 4000,
                          "discount": 0,
                          "totalPrice": 80000,
                          "unit": "Cây",
                          "warehouse": "Kho 2 (KH002)",
                          "nameConfidence": 0.9,
                          "codeConfidence": 0.87,
                          "quantityConfidence": 0.93,
                          "unitPriceConfidence": 0.9,
                          "totalPriceConfidence": 0.9
                        }
                      ],
                      "totalAmount": 80000
                    }

                    Lưu ý:
                    - Chỉ trả về JSON, không thêm text khác
                    - Nếu không tìm thấy thông tin, để giá trị null hoặc rỗng
                    - Đảm bảo tất cả số liệu là chính xác
                    - Tên sản phẩm phải rõ ràng, không viết tắt
                    - Với số tiền, bỏ dấu chấm/phẩy phân cách hàng nghìn (ví dụ: "1.850.000" -> 1850000)
                    - Nếu ảnh là form web đã điền, đọc trực tiếp từ các field và table
                    - CỰC KỲ QUAN TRỌNG VỀ KHO HÀNG - ĐỌC TỪNG HÀNG ĐỘC LẬP:
                      * Mỗi hàng sản phẩm trong bảng có thể có kho hàng HOÀN TOÀN KHÁC NHAU
                      * BẮT BUỘC phải đọc từng HÀNG NGANG một cách tuần tự, từ trên xuống
                      * Với mỗi hàng, tìm cột "Kho xuất" và đọc chính xác text trong ô đó
                      * KHÔNG ĐƯỢC copy kho hàng từ hàng trước
                      * KHÔNG ĐƯỢC giả định tất cả đều cùng một kho
                      * KHÔNG ĐƯỢC bỏ qua việc đọc kho hàng của bất kỳ hàng nào
                      * Ví dụ cụ thể: Nếu hàng 1 (STT 1) có 'kho trung tâm Hà Nội', hàng 2 (STT 2) có 'kho hồ Chí Minh', hàng 3 (STT 3) có 'Kho trung tâm Hà Nội', thì:
                        - products[0].warehouse = "kho trung tâm Hà Nội"
                        - products[1].warehouse = "kho hồ Chí Minh" (KHÔNG PHẢI "kho trung tâm Hà Nội")
                        - products[2].warehouse = "Kho trung tâm Hà Nội"
                      * Đọc đầy đủ tên kho (ví dụ: 'kho trung tâm Hà Nội', 'kho hồ Chí Minh', không được viết tắt thành 'Hà Nội' hoặc 'HCM')
                      * Nếu có mã trong ngoặc thì đọc cả mã (ví dụ: "Kho 1 (KH001)")
                      * QUAN TRỌNG: Khi đọc hàng STT 2, PHẢI nhìn vào ô "Kho xuất" của hàng STT 2, không được dùng giá trị từ hàng STT 1
                      * QUAN TRỌNG: Đọc theo HÀNG NGANG (row-by-row), không đọc theo cột dọc
                    - RẤT QUAN TRỌNG: Khi đọc tên khách hàng, phải đọc ĐẦY ĐỦ toàn bộ tên từ đầu đến cuối, bao gồm cả họ, tên đệm và tên. Không được cắt ngắn hoặc chỉ lấy phần cuối. Ví dụ: "Đỗ Quốc Huy" phải đọc đầy đủ là "Đỗ Quốc Huy", KHÔNG được chỉ đọc "Huy" hoặc "Quốc Huy"
                    - Khi đọc tên, hãy đọc toàn bộ text trong field "Tên khách hàng" hoặc "Khách hàng", không được bỏ sót bất kỳ từ nào
                    - Trả thêm các trường confidence (0-1) cho từng sản phẩm nếu có: nameConfidence, codeConfidence, quantityConfidence, unitPriceConfidence, totalPriceConfidence
                    - RẤT QUAN TRỌNG VỀ MÃ HÀNG: Khi đọc mã hàng, phải loại bỏ TẤT CẢ khoảng trắng thừa giữa các ký tự. Mã hàng thường là chuỗi liên tục không có khoảng trắng (ví dụ: 'SPDT002' chứ không phải 'SPDT 002'). Nếu trong ảnh có khoảng trắng, hãy tự động loại bỏ để trả về mã hàng chuẩn. Đọc chính xác từng ký tự, phân biệt số 0 và chữ O, số 1 và chữ I.
                    - RẤT QUAN TRỌNG VỀ ĐƠN GIÁ: unitPrice phải là giá GỐC TRƯỚC CHIẾT KHẤU. Nếu trong ảnh cột 'Đơn giá' đã là giá sau chiết khấu, hãy tính ngược lại từ Thành tiền: unitPrice = totalPrice / quantity / (1 - discount/100). Ví dụ: Nếu Thành tiền = 260.910.000, SL = 10, Chiết khấu = 10%, thì unitPrice phải là 28.990.000 (KHÔNG phải 26.091.000). Luôn đảm bảo: unitPrice * quantity * (1 - discount/100) = totalPrice.
                    - RẤT QUAN TRỌNG VỀ MÃ HÀNG: Khi đọc mã hàng, phải loại bỏ TẤT CẢ khoảng trắng thừa giữa các ký tự. Mã hàng thường là chuỗi liên tục không có khoảng trắng (ví dụ: 'SPDT002' chứ không phải 'SPDT 002'). Nếu trong ảnh có khoảng trắng, hãy tự động loại bỏ để trả về mã hàng chuẩn. Đọc chính xác từng ký tự, phân biệt số 0 và chữ O, số 1 và chữ I.
                    - RẤT QUAN TRỌNG VỀ ĐƠN GIÁ: unitPrice phải là giá GỐC TRƯỚC CHIẾT KHẤU. Nếu trong ảnh cột 'Đơn giá' đã là giá sau chiết khấu, hãy tính ngược lại từ Thành tiền: unitPrice = totalPrice / quantity / (1 - discount/100). Ví dụ: Nếu Thành tiền = 260.910.000, SL = 10, Chiết khấu = 10%, thì unitPrice phải là 28.990.000 (KHÔNG phải 26.091.000). Luôn đảm bảo: unitPrice * quantity * (1 - discount/100) = totalPrice.
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
                    product.setNameConfidence(getDoubleValue(productNode, "nameConfidence"));
                    product.setCodeConfidence(getDoubleValue(productNode, "codeConfidence"));
                    product.setQuantityConfidence(getDoubleValue(productNode, "quantityConfidence"));
                    product.setUnitPriceConfidence(getDoubleValue(productNode, "unitPriceConfidence"));
                    product.setTotalPriceConfidence(getDoubleValue(productNode, "totalPriceConfidence"));
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

    private String buildSearchText(UpdateReceiptMetadataRequest request) {
        StringBuilder text = new StringBuilder();

        if ("IMPORT".equalsIgnoreCase(request.getReceiptType())) {
            if (request.getSupplierName() != null) {
                text.append("Nhà cung cấp: ").append(request.getSupplierName()).append(" ");
            }
        } else {
            if (request.getCustomerName() != null) {
                text.append("Khách hàng: ").append(request.getCustomerName()).append(" ");
            }
        }

        if (request.getProducts() != null) {
            for (UpdateReceiptMetadataRequest.ProductMetadata product : request.getProducts()) {
                if (product.getName() != null) {
                    text.append("Sản phẩm: ").append(product.getName()).append(" ");
                }
                if (product.getCode() != null) {
                    text.append("Mã: ").append(product.getCode()).append(" ");
                }
                if (product.getProductId() != null) {
                    text.append("ID: ").append(product.getProductId()).append(" ");
                }
            }
        }
        return text.toString().trim();
    }

    /**
     * Chuẩn hóa L2 distance score từ Milvus thành matchScore 0-1
     * Score thấp (L2 distance nhỏ) = similarity cao = matchScore cao
     * Công thức: matchScore = 1 / (1 + distance) để đảo ngược và chuẩn hóa về 0-1
     */
    private Double normalizeMatchScore(Double l2Distance) {
        if (l2Distance == null || l2Distance < 0) {
            return 0.0;
        }
        // Chuẩn hóa: score thấp (distance nhỏ) -> matchScore cao
        // Giới hạn tối đa distance = 10 để matchScore không quá thấp
        double normalizedDistance = Math.min(l2Distance, 10.0);
        return 1.0 / (1.0 + normalizedDistance);
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

            if (response.getProducts() == null || response.getProducts().isEmpty()) {
                return;
            }

            for (ReceiptOCRResponse.ExtractedProduct product : response.getProducts()) {
                double bestScore = 0.0;
                Long bestProductId = null;

                for (Map<String, Object> candidate : similarReceipts) {
                    Double l2Distance = (Double) candidate.get("score");
                    Double baseScore = normalizeMatchScore(l2Distance);
                    if (baseScore == null) {
                        baseScore = 0.0;
                    }

                    String metadataStr = (String) candidate.get("metadata");
                    if (metadataStr == null || metadataStr.isEmpty()) {
                        continue;
                    }

                    try {
                        JsonNode metadata = objectMapper.readTree(metadataStr);

                        // supplier/customer weight
                        double weightedScore = baseScore;
                        if ("IMPORT".equals(receiptType) && response.getSupplierName() != null
                                && metadata.has("supplierName")
                                && response.getSupplierName().equalsIgnoreCase(metadata.get("supplierName").asText())) {
                            weightedScore *= 1.1;
                        }
                        if ("EXPORT".equals(receiptType) && response.getCustomerName() != null
                                && metadata.has("customerName")
                                && response.getCustomerName().equalsIgnoreCase(metadata.get("customerName").asText())) {
                            weightedScore *= 1.1;
                        }

                        if (metadata.has("products") && metadata.get("products").isArray()) {
                            for (JsonNode p : metadata.get("products")) {
                                double score = weightedScore;

                                String pCode = p.has("code") && !p.get("code").isNull() ? p.get("code").asText() : null;
                                String pName = p.has("name") && !p.get("name").isNull() ? p.get("name").asText() : null;
                                String pWarehouse = p.has("warehouse") && !p.get("warehouse").isNull()
                                        ? p.get("warehouse").asText()
                                        : null;

                                // Exact code match gets highest priority
                                if (product.getCode() != null && pCode != null
                                        && product.getCode().equalsIgnoreCase(pCode)) {
                                    score = 1.0;
                                } else {
                                    // name fuzzy/contains
                                    if (product.getName() != null && pName != null) {
                                        boolean nameMatches = product.getName().equalsIgnoreCase(pName)
                                                || product.getName().toLowerCase().contains(pName.toLowerCase())
                                                || pName.toLowerCase().contains(product.getName().toLowerCase());
                                        if (nameMatches) {
                                            score *= 1.05;
                                        }
                                    }
                                }

                                // warehouse weight
                                if (product.getWarehouse() != null && pWarehouse != null
                                        && pWarehouse.toLowerCase().contains(product.getWarehouse().toLowerCase())) {
                                    score *= 1.05;
                                }

                                Long pId = p.has("productId") && !p.get("productId").isNull()
                                        ? p.get("productId").asLong()
                                        : null;

                                if (score > bestScore && pId != null) {
                                    bestScore = score;
                                    bestProductId = pId;
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse metadata from similar receipt", e);
                    }
                }

                if (bestProductId != null && bestScore >= 0.6) {
                    product.setSuggestedProductId(bestProductId);
                    product.setMatchScore(bestScore);
                    log.info("✅ Matched product '{}' -> productId {} with score {:.2f}", product.getName(),
                            bestProductId, bestScore);
                } else {
                    log.debug("❌ No strong match for product '{}' (code: {})", product.getName(), product.getCode());
                }
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
