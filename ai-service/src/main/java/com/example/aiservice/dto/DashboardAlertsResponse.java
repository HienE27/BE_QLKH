package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardAlertsResponse {
    private List<Alert> alerts;
    private String summary;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Alert {
        private String type; // CRITICAL, WARNING, INFO, SUCCESS
        private String title;
        private String message;
        private String action; // URL hoặc action gợi ý
        private String icon;
    }
}

