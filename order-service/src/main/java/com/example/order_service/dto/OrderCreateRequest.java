package com.example.order_service.dto;

import lombok.Data;

import java.util.List;

@Data
public class OrderCreateRequest {

    private Long customerId;
    private Long paymentTypesId;

    private String shipName;
    private String shipAddress;
    private String shipCity;
    private String shipCountry;

    // mã voucher người dùng nhập
    private String voucherCode;

    // danh sách sản phẩm trong đơn
    private List<OrderItemRequest> items;
}
