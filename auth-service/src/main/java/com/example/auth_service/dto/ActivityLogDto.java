package com.example.auth_service.dto;

import com.example.auth_service.entity.ActivityLog;
import lombok.Data;

import java.util.Date;

@Data
public class ActivityLogDto {
    private Long id;
    private Long userId;
    private String username;
    private String displayName;
    private String action;
    private String resourceType;
    private Long resourceId;
    private String resourceName;
    private String details;
    private String ipAddress;
    private String userAgent;
    private Date createdAt;

    public static ActivityLogDto fromEntity(ActivityLog log) {
        ActivityLogDto dto = new ActivityLogDto();
        dto.setId(log.getId());
        dto.setUserId(log.getUserId());
        dto.setUsername(log.getUsername());
        dto.setDisplayName(log.getDisplayName());
        dto.setAction(log.getAction());
        dto.setResourceType(log.getResourceType());
        dto.setResourceId(log.getResourceId());
        dto.setResourceName(log.getResourceName());
        dto.setDetails(log.getDetails());
        dto.setIpAddress(log.getIpAddress());
        dto.setUserAgent(log.getUserAgent());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }
}

