package com.example.auth_service.service;

public interface EmailService {
    void sendEmailVerification(String to, String token, String username);
    void sendPasswordResetEmail(String to, String token, String username);
    void sendUnlockCodeEmail(String to, String code, String username, java.util.Date expiry);
}


