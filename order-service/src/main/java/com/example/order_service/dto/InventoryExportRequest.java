package com.example.order_service.dto;

import lombok.Data;

import java.util.List;

@Data
public class InventoryExportRequest {

    private Long storeId;      // kho xuất (shop_stores.stores_id)
    private Long userId;       // nhân viên tạo đơn (ad_users.user_id)
    private Long orderId;      // đơn hàng liên quan (shop_orders.order_id)
    private String note;       // ghi chú phiếu xuất

    private List<InventoryExportDetailRequest> details;
}
