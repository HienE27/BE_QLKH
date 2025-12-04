package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private String reportType;
    private String title;
    private String summary;
    private String htmlContent; // HTML formatted report
    private String highlights; // Key insights
    private String recommendations;
}

