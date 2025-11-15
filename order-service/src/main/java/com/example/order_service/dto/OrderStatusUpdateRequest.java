package com.example.order_service.dto;

import lombok.Data;

@Data
public class OrderStatusUpdateRequest {

    // Ví dụ: NEW, PAID, SHIPPED, DONE, CANCELLED
    private String status;
}
