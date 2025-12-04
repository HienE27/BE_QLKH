package com.example.order_service.dto;

import lombok.Data;

// DTO để map ApiResponse từ promotion-service:

@Data
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
}
