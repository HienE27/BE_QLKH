package com.example.inventory_service.dto;

import lombok.Data;

import java.util.List;

@Data
public class ImportRequest {

    private Long storeId;   // stores_id
    private Long userId;    // user_id (nhân viên nhập kho)
    private String note;

    private List<ImportDetailRequest> details;
}
