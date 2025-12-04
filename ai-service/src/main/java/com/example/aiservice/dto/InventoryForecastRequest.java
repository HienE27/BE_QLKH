package com.example.aiservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class InventoryForecastRequest {

    @NotEmpty(message = "items is required")
    @Valid
    private List<ItemSummary> items;

    @Data
    public static class ItemSummary {
        @NotBlank(message = "code is required")
        private String code;
        @NotBlank(message = "name is required")
        private String name;
        @NotNull(message = "quantity is required")
        private Integer quantity;
        private Double avgDailySales;
    }
}


