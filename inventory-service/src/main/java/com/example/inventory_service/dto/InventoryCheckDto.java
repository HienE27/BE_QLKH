package com.example.inventory_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
public class InventoryCheckDto {
    private Long id;
    private String checkCode;

    private Long storeId;
    private String storeName;

    private String description;
    private String status; // PENDING, APPROVED, REJECTED

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Ho_Chi_Minh")
    private Date checkDate;

    private Long createdBy;
    private String createdByName;

    private Long approvedBy;
    private String approvedByName;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Ho_Chi_Minh")
    private Date approvedAt;

    private String note;
    private List<String> attachmentImages;

    private BigDecimal totalDifferenceValue; // Tổng giá trị chênh lệch

    private List<InventoryCheckDetailDto> items;
}
