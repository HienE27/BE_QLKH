package com.example.auth_service.service;

import com.example.auth_service.dto.LoginRequest;
import com.example.auth_service.dto.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest req);
}