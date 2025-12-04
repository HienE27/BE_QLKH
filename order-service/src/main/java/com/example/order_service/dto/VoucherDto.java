package com.example.order_service.dto;

import lombok.Data;

import java.time.LocalDateTime;

// DTO VoucherDto ở order-service (để đọc dữ liệu từ promotion-service)

@Data
public class VoucherDto {
 private Long id;
    private String code;
    private String description;
    private Double discountAmount;   // lấy discountAmount từ promotion-service
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
