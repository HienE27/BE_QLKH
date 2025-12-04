package com.example.order_service.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderRequest {
    private Long customerId;
    private Long paymentTypeId;
    private Long userId;

    private String shipName;
    private String shipAddress;
    private String shipCity;
    private String shipCountry;
    private String shipPostalCode;
    private BigDecimal shipFee;

    //  Thêm kho xuất hàng
    private Long storeId;      // shop_stores.store_id

    // Voucher
    private String voucherCode;

    private List<OrderDetailRequest> details;
}
