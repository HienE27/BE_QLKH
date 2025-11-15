package com.example.inventory_service.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class ExportDto {
    private Long id;
    private Long storeId;
    private Long userId;
    private Long orderId;
    private String note;
    private String description;
    private Date exportsDate;
    private Date createdAt;
    private Date updatedAt;

    private List<ExportDetailDto> details;
}
