package com.example.promotion_service.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VoucherRequest {
    private String code;
    private String description;
    private String type;          // AMOUNT / PERCENT
    private Double discountAmount;
    private Integer maxUser;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
