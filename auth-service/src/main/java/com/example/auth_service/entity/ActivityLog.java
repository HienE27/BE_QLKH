package com.example.auth_service.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Entity
@Table(name = "activity_logs")
@Data
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username")
    private String username;
    
    @Column(name = "display_name")
    private String displayName;

    @Column(name = "action")
    private String action; // LOGIN, CREATE_USER, UPDATE_USER, DELETE_USER, etc.

    @Column(name = "resource_type")
    private String resourceType; // USER, ROLE, PERMISSION, etc.

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "resource_name")
    private String resourceName;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details; // JSON string with additional details

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at")
    private Date createdAt;
}

