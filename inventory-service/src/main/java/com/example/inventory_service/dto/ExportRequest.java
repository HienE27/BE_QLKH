package com.example.inventory_service.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExportRequest {
    private Long storeId;
    private Long userId;
    private Long orderId;
    private String note;
    private String description;
    private List<ExportDetailRequest> details;
}
