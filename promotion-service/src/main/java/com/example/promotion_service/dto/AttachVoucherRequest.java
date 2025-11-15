package com.example.promotion_service.dto;

import lombok.Data;

import java.util.List;

@Data
public class AttachVoucherRequest {
    private List<Long> ids; // danh sách productId hoặc customerId
}
