package com.example.aiservice.service;

import com.example.aiservice.dto.ProductDescriptionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductDescriptionParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProductDescriptionResponse parse(String raw) {
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("```json", "")
                    .replaceFirst("```", "")
                    .trim();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(cleaned, Map.class);

            return ProductDescriptionResponse.builder()
                    .shortDescription((String) map.getOrDefault("short", ""))
                    .seoDescription((String) map.getOrDefault("seo", ""))
                    .longDescription((String) map.getOrDefault("long", ""))
                    .attributes(extractAttributes(map.get("attributes")))
                    .build();
        } catch (JsonProcessingException e) {
            log.warn("Không parse được JSON mô tả sản phẩm: {}", e.getMessage());
            return ProductDescriptionResponse.builder()
                    .shortDescription(raw)
                    .seoDescription(raw)
                    .longDescription(raw)
                    .attributes(Collections.emptyList())
                    .build();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractAttributes(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof String)
                    .map(Object::toString)
                    .toList();
        }
        return Collections.emptyList();
    }
}


