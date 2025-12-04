package com.example.order_service.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
public class OrderDto {
    private Long id;
    private Long customerId;
    private Long paymentTypeId;
    private Long userId;

    private String shipName;
    private String shipAddress;
    private String shipCity;
    private String shipCountry;
    private String shipPostalCode;
    private BigDecimal shipFee;

    private Date orderDate;
    private String orderStatus;

    private Double totalAmount;
    private Double discountAmount;


    // Danh sách chi tiết
    private List<OrderDetailDto> details;
}
