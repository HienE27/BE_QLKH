package com.example.order_service.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderSummaryDto {
    private long totalOrders;          // số lượng đơn
    private BigDecimal totalAmount;    // tổng tiền sau giảm (total_amount)
    private BigDecimal totalDiscount;  // tổng tiền giảm (discount_amount)
}
