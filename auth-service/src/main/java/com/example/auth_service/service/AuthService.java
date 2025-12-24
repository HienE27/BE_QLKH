package com.example.auth_service.service;

import com.example.auth_service.dto.ChangePasswordRequest;
import com.example.auth_service.dto.ForgotPasswordRequest;
import com.example.auth_service.dto.LoginRequest;
import com.example.auth_service.dto.LoginResponse;
import com.example.auth_service.dto.RefreshTokenRequest;
import com.example.auth_service.dto.ResetPasswordWithTokenRequest;
import com.example.auth_service.dto.TokenPairResponse;
import com.example.auth_service.dto.UpdateProfileRequest;
import com.example.auth_service.dto.UserProfileDto;

public interface AuthService {
    LoginResponse login(LoginRequest req);
    TokenPairResponse refreshToken(RefreshTokenRequest request);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPasswordWithToken(ResetPasswordWithTokenRequest request);
    void verifyEmail(String token);
    void resendVerificationEmail(String username);
    void verifyUnlockCode(String username, String code);
    UserProfileDto getCurrentUserProfile(String username);
    UserProfileDto updateProfile(String username, UpdateProfileRequest request);
    void deleteAccount(String username);
    void changePassword(String username, ChangePasswordRequest request);
    void logout(String username);
}