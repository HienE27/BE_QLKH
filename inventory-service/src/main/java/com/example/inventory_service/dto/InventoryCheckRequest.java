package com.example.inventory_service.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class InventoryCheckRequest {
    private String checkCode; // Optional, BE sẽ tự sinh nếu null
    private Long storeId;
    private String description;
    private Date checkDate;
    private String note;
    private List<String> attachmentImages;
    private List<InventoryCheckDetailRequest> items;
}
