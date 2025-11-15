package com.example.inventory_service.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class ImportDto {

    private Long id;
    private Long storeId;
    private Long userId;
    private String note;
    private Date createdAt;
    private Date updatedAt;

    private List<ImportDetailDto> details;
}
