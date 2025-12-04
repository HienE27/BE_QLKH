package com.example.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final WebClient geminiWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api-key}")
    private String apiKey;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String EMBEDDING_MODEL = "models/text-embedding-004"; // 768 dimensions

    /**
     * Tạo embedding từ text
     * 
     * @param text Text cần tạo embedding
     * @return List<Float> embedding vector (768 dimensions cho text-embedding-004)
     */
    public List<Float> generateEmbedding(String text) {
        // Trim API key để loại bỏ khoảng trắng thừa
        String trimmedApiKey = (apiKey != null) ? apiKey.trim() : null;

        if (trimmedApiKey == null || trimmedApiKey.isBlank()) {
            throw new IllegalStateException("gemini.api-key is not configured");
        }

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }

        try {
            // Gemini Embedding API format
            Map<String, Object> body = new HashMap<>();
            body.put("model", EMBEDDING_MODEL);
            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(Map.of("text", text)));
            body.put("content", content);

            log.debug("Calling Gemini Embedding API with model: {}", EMBEDDING_MODEL);

            String response = geminiWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/" + EMBEDDING_MODEL + ":embedContent")
                            .queryParam("key", trimmedApiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            clientResponse -> clientResponse.bodyToMono(String.class).map(msg -> {
                                log.error("Gemini embedding error response ({}): {}",
                                        clientResponse.statusCode(), msg);
                                return new RuntimeException("Gemini Embedding API error: " + msg);
                            }))
                    .bodyToMono(String.class)
                    .block(TIMEOUT);

            if (response == null || response.isBlank()) {
                log.error("Received null or empty response from Gemini Embedding API");
                throw new RuntimeException("Không nhận được phản hồi từ Gemini Embedding API");
            }

            log.debug("Received response from Gemini Embedding API: {}",
                    response.substring(0, Math.min(200, response.length())));

            // Parse response để lấy embedding
            JsonNode jsonResponse = objectMapper.readTree(response);

            // Kiểm tra lỗi trong response
            if (jsonResponse.has("error")) {
                JsonNode errorNode = jsonResponse.get("error");
                String errorMessage = errorNode.has("message")
                        ? errorNode.get("message").asText()
                        : errorNode.toString();
                log.error("Gemini API returned error: {}", errorMessage);
                throw new RuntimeException("Gemini Embedding API error: " + errorMessage);
            }

            // Tìm embedding trong response
            JsonNode embeddingNode = jsonResponse.path("embedding");

            if (embeddingNode.isMissingNode()) {
                log.error("Response structure: {}", jsonResponse.toPrettyString());
                throw new RuntimeException("Không tìm thấy field 'embedding' trong response: " + response);
            }

            List<Float> embedding = new ArrayList<>();

            // Lấy values từ embedding
            JsonNode valuesNode = embeddingNode.path("values");
            if (valuesNode.isMissingNode() || !valuesNode.isArray()) {
                log.error("Response structure: {}", jsonResponse.toPrettyString());
                throw new RuntimeException("Không tìm thấy 'embedding.values' array trong response: " + response);
            }

            for (JsonNode value : valuesNode) {
                embedding.add((float) value.asDouble());
            }

            if (embedding.isEmpty()) {
                throw new RuntimeException("Embedding vector rỗng trong response: " + response);
            }

            log.info("Generated embedding with {} dimensions", embedding.size());
            return embedding;

        } catch (WebClientResponseException ex) {
            log.error("Gemini Embedding HTTP error {} - Response body: {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw new RuntimeException(
                    "Gemini Embedding API error: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("Gemini embedding generation failed", ex);
            throw new RuntimeException("Không thể tạo embedding: " + ex.getMessage(), ex);
        }
    }

    /**
     * Tạo embedding từ nhiều text (batch)
     */
    public List<List<Float>> generateEmbeddings(List<String> texts) {
        List<List<Float>> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(generateEmbedding(text));
        }
        return embeddings;
    }
}
