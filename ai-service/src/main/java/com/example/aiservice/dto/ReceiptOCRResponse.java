package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptOCRResponse {
    private String receiptType; // "IMPORT" hoặc "EXPORT"

    // Thông tin chung
    private String supplierName; // Tên nhà cung cấp (cho phiếu nhập)
    private String customerName; // Tên khách hàng (cho phiếu xuất)
    private String supplierPhone; // SĐT nhà cung cấp
    private String customerPhone; // SĐT khách hàng
    private String supplierAddress; // Địa chỉ nhà cung cấp
    private String customerAddress; // Địa chỉ khách hàng
    private String receiptCode; // Mã phiếu
    private String receiptDate; // Ngày phiếu
    private String note; // Lý do/Ghi chú

    // Danh sách sản phẩm trích xuất từ ảnh
    private List<ExtractedProduct> products;

    // Tổng tiền
    private Double totalAmount;

    // Các phiếu tương tự (lấy từ Milvus) để gợi ý cho người dùng
    private List<SimilarReceipt> similarReceipts;

    // Metadata
    private String rawText; // Text thô từ OCR
    private Double confidence; // Độ tin cậy

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedProduct {
        private String name; // Tên sản phẩm
        private String code; // Mã sản phẩm (nếu có)
        private Integer quantity; // Số lượng
        private Double unitPrice; // Đơn giá
        private Double discount; // Chiết khấu (%)
        private Double totalPrice; // Thành tiền
        private String unit; // Đơn vị tính
        private String warehouse; // Tên kho hàng (từ cột 'Kho nhập' hoặc 'Kho xuất', ví dụ: "Kho 1 (KH001)")
        private Long suggestedProductId; // ProductId gợi ý từ vector search (nếu có)
        private Double matchScore; // Độ tương tự từ vector search (nếu có)
    }

    /**
     * Thông tin tóm tắt về các phiếu tương tự trong Milvus.
     * Dùng để hiển thị gợi ý cho người dùng trên màn hình tạo phiếu nhập/xuất.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarReceipt {
        private Long id; // ID nội bộ trong Milvus (không phải id business)
        private Double score; // Score khoảng cách vector (thấp hơn = tương tự hơn)

        private String receiptType;
        private String supplierName;
        private String customerName;
        private String receiptCode;
        private String receiptDate;
        private Double totalAmount;
        private String noteSummary; // Ghi chú rút gọn (nếu có)
    }
}
