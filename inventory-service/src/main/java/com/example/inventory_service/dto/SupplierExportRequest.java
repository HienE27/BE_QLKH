package com.example.inventory_service.dto;

import lombok.Data;
import java.util.List;

@Data
public class SupplierExportRequest {
    private String code; // optional, nếu null BE tự sinh
    private Long storeId; // Kho xuất (bắt buộc)
    private Long customerId; // ID khách hàng (bắt buộc)
    private Long orderId; // ID đơn hàng (nếu xuất theo đơn)

    // Thông tin khách hàng (nếu xuất trực tiếp không qua shop_customers)
    private String customerName; // Bắt buộc nếu không có customerId
    private String customerPhone;
    private String customerAddress;

    private String note;
    private String description;
    private List<String> attachmentImages; // đường dẫn ảnh FE gửi (/uploads/... hoặc full URL)
    private List<ExportDetailRequest> items;
}
