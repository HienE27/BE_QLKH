package com.example.inventory_service.dto;

import lombok.Data;
import java.util.List;

@Data
public class SupplierImportRequest {

    private String code; // optional: nếu không gửi, BE sẽ tự sinh

    private Long storeId; // Kho nhập (bắt buộc)
    private Long supplierId; // ID nhà cung cấp (bắt buộc)
    private Long orderId; // ID đơn hàng (nếu nhập theo đơn)

    private String note;
    private String description;

    private List<String> attachmentImages; // đường dẫn ảnh FE gửi (/uploads/... hoặc full URL)

    private List<ImportDetailRequest> items;
}
