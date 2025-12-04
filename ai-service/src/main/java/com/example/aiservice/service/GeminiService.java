package com.example.aiservice.service;

import com.example.aiservice.model.GeminiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final WebClient geminiWebClient;

    @Value("${gemini.api-key}")
    private String apiKey;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String MODEL_PATH = "/models/gemini-2.0-flash-001:generateContent";

    public String invokeGemini(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("gemini.api-key is not configured");
        }

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("text", prompt))
                        )
                )
        );

        try {
            GeminiResponse response = geminiWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(MODEL_PATH)
                            .queryParam("key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse ->
                            clientResponse.bodyToMono(String.class).map(msg -> {
                                log.error("Gemini error response: {}", msg);
                                return new RuntimeException("Gemini API error: " + msg);
                            }))
                    .bodyToMono(GeminiResponse.class)
                    .block(TIMEOUT);

            if (response == null) {
                throw new RuntimeException("Không nhận được phản hồi từ Gemini");
            }
            String text = response.firstText();
            if (text == null) {
                throw new RuntimeException("Không có nội dung trả về từ Gemini");
            }
            return text.trim();
        } catch (WebClientResponseException ex) {
            log.error("Gemini HTTP error {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw new RuntimeException("Gemini API error: " + ex.getStatusCode());
        } catch (Exception ex) {
            log.error("Gemini invocation failed", ex);
            throw new RuntimeException("Không thể kết nối Gemini: " + ex.getMessage());
        }
    }
}


