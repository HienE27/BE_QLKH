package com.example.auth_service.controller;

import com.example.auth_service.common.ApiResponse;
import com.example.auth_service.dto.ChangePasswordRequest;
import com.example.auth_service.dto.ForgotPasswordRequest;
import com.example.auth_service.dto.LoginRequest;
import com.example.auth_service.dto.LoginResponse;
import com.example.auth_service.dto.RefreshTokenRequest;
import com.example.auth_service.dto.ResendVerificationRequest;
import com.example.auth_service.dto.ResetPasswordWithTokenRequest;
import com.example.auth_service.dto.TokenPairResponse;
import com.example.auth_service.dto.UpdateProfileRequest;
import com.example.auth_service.dto.UserProfileDto;
import com.example.auth_service.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse res = authService.login(request);
        return ApiResponse.ok("Login success", res);
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenPairResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        TokenPairResponse res = authService.refreshToken(request);
        return ApiResponse.ok("Refresh success", res);
    }

    @PostMapping("/forgot-password")
    public ApiResponse<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        // always return success to avoid user enumeration
        return ApiResponse.ok("Nếu email tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi.", null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<String> resetPassword(@Valid @RequestBody ResetPasswordWithTokenRequest request) {
        authService.resetPasswordWithToken(request);
        return ApiResponse.ok("Đặt lại mật khẩu thành công", null);
    }

    @GetMapping("/verify-email")
    public ApiResponse<String> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ApiResponse.ok("Xác thực email thành công", null);
    }

    @PostMapping("/resend-verification")
    public ApiResponse<String> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerificationEmail(request.getUsername());
        return ApiResponse.ok("Nếu tài khoản cần xác thực email, hệ thống đã gửi lại email xác thực.", null);
    }

    @PostMapping("/verify-unlock")
    public ApiResponse<String> verifyUnlock(@RequestBody com.example.auth_service.dto.VerifyUnlockRequest request) {
        authService.verifyUnlockCode(request.getUsername(), request.getCode());
        return ApiResponse.ok("Mở khóa thành công", null);
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfileDto> getProfile() {
        String username = getCurrentUsername();
        UserProfileDto profile = authService.getCurrentUserProfile(username);
        return ApiResponse.ok(profile);
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileDto> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        String username = getCurrentUsername();
        UserProfileDto profile = authService.updateProfile(username, request);
        return ApiResponse.ok("Cập nhật thông tin thành công", profile);
    }

    @DeleteMapping("/profile")
    public ApiResponse<String> deleteAccount() {
        String username = getCurrentUsername();
        authService.deleteAccount(username);
        return ApiResponse.ok("Đã vô hiệu hóa tài khoản");
    }

    @PutMapping("/change-password")
    public ApiResponse<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String username = getCurrentUsername();
        authService.changePassword(username, request);
        return ApiResponse.ok("Đổi mật khẩu thành công");
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout() {
        String username = getCurrentUsername();
        authService.logout(username);
        return ApiResponse.ok("Đăng xuất thành công");
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return auth.getName();
        }
        throw new RuntimeException("User not authenticated");
    }
}