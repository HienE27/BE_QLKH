package com.example.auth_service.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private UserDto user;
}