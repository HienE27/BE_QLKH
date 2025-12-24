package com.example.auth_service.dto;

import lombok.Data;

@Data
public class VerifyUnlockRequest {
    private String username;
    private String code;
}


