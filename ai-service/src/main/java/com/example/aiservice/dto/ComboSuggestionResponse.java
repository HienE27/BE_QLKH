package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComboSuggestionResponse {
    private List<ComboSuggestion> combos;
    private String analysis;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComboSuggestion {
        private String name;
        private List<ComboItem> items;
        private Double originalPrice;
        private Double comboPrice;
        private Double discount;
        private String reason;
        private String targetCustomer;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComboItem {
        private String code;
        private String name;
        private Double price;
        private Integer quantity;
    }
}

