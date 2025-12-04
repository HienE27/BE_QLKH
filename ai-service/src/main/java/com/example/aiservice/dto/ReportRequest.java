package com.example.aiservice.dto;

import lombok.Data;

@Data
public class ReportRequest {
    private String reportType; // INVENTORY, SALES, IMPORT_EXPORT
    private String period; // DAILY, WEEKLY, MONTHLY
    private String startDate;
    private String endDate;
    private String format; // PDF, EXCEL, HTML
}

