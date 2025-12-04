package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceSuggestionResponse {
    private Double suggestedPrice;
    private Double minPrice;
    private Double maxPrice;
    private String strategy; // DISCOUNT, MAINTAIN, INCREASE
    private String reasoning;
    private Double expectedProfit;
    private String promotionSuggestion;
}

