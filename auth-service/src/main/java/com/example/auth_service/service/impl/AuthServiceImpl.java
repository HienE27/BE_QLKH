package com.example.auth_service.service.impl;

import com.example.auth_service.dto.ChangePasswordRequest;
import com.example.auth_service.dto.ForgotPasswordRequest;
import com.example.auth_service.dto.LoginRequest;
import com.example.auth_service.dto.LoginResponse;
import com.example.auth_service.dto.RefreshTokenRequest;
import com.example.auth_service.dto.ResetPasswordWithTokenRequest;
import com.example.auth_service.dto.TokenPairResponse;
import com.example.auth_service.dto.UpdateProfileRequest;
import com.example.auth_service.dto.UserProfileDto;
import com.example.auth_service.entity.AdUser;
import com.example.auth_service.entity.RefreshToken;
import com.example.auth_service.exception.NotFoundException;
import com.example.auth_service.exception.BadRequestException;
import com.example.auth_service.repository.AdUserRepository;
import com.example.auth_service.security.JwtService;
import com.example.auth_service.service.AuthService;
import com.example.auth_service.service.EmailService;
import com.example.auth_service.service.RefreshTokenService;
import com.example.auth_service.service.RateLimitingService;
import com.example.auth_service.service.TokenBlacklistService;
import com.example.auth_service.util.ActivityLogHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Calendar;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AdUserRepository userRepository;
    private final ActivityLogHelper activityLogHelper;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitingService rateLimitingService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final EmailService emailService;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           JwtService jwtService,
                           AdUserRepository userRepository,
                           ActivityLogHelper activityLogHelper,
                           PasswordEncoder passwordEncoder,
                           RateLimitingService rateLimitingService,
                           RefreshTokenService refreshTokenService,
                           TokenBlacklistService tokenBlacklistService,
                           EmailService emailService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.activityLogHelper = activityLogHelper;
        this.passwordEncoder = passwordEncoder;
        this.rateLimitingService = rateLimitingService;
        this.refreshTokenService = refreshTokenService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.emailService = emailService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        AdUser user = userRepository.findByUsername(request.getUsername()).orElse(null);

        // Get client IP for rate limiting
        String clientIp = getClientIp();
        String rateLimitKey = request.getUsername() + ":" + clientIp;

        // Check rate limiting before authentication
        if (!rateLimitingService.isAllowed(rateLimitKey)) {
            long remainingSeconds = rateLimitingService.getRemainingSeconds(rateLimitKey);
            throw new BadRequestException(
                String.format("Quá nhiều lần đăng nhập sai. Vui lòng thử lại sau %d giây.", remainingSeconds)
            );
        }

        // Kiểm tra tài khoản có đang bị khóa không
        if (user != null && user.getAccountLockedUntil() != null && user.getAccountLockedUntil().after(new Date())) {
            throw new BadRequestException("Tài khoản đã bị khóa tạm thời. Vui lòng thử lại sau.");
        }

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // lấy UserDetails từ Authentication
            org.springframework.security.core.userdetails.User userDetails =
                    (org.springframework.security.core.userdetails.User) auth.getPrincipal();

            String token = jwtService.generateToken(userDetails);

            List<String> roles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();

            // Lấy lại user từ DB nếu cần
            if (user == null) {
                user = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
            }

            // refresh token (opaque) - revoke old tokens for single-session behavior
            RefreshToken refreshToken = null;
            if (user != null) {
                refreshTokenService.revokeAllUserTokens(user.getId());
                refreshToken = refreshTokenService.createRefreshToken(user.getId());
            }

            if (user != null) {
                // Reset failed attempts & unlock account
                user.setFailedLoginAttempts(0);
                user.setAccountLockedUntil(null);
                user.setUpdatedAt(new Date());
                userRepository.save(user);

                // Clear rate limiting on successful login
                rateLimitingService.clearAttempts(rateLimitKey);

                // Log login activity
                activityLogHelper.logActivity(
                        user.getId(),
                        user.getUsername(),
                        "LOGIN",
                        "USER",
                        user.getId(),
                        user.getUsername(),
                        "User logged in successfully"
                );
            }

            return new LoginResponse(
                    token,
                    refreshToken != null ? refreshToken.getToken() : null,
                    userDetails.getUsername(),
                    roles
            );
        } catch (BadCredentialsException ex) {
            // Sai mật khẩu
            if (user != null) {
                int attempts = user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() + 1 : 1;
                user.setFailedLoginAttempts(attempts);

                // Log failed login
                activityLogHelper.logActivity(
                        user.getId(),
                        user.getUsername(),
                        "FAILED_LOGIN",
                        "USER",
                        user.getId(),
                        user.getUsername(),
                        "Failed login attempt"
                );

                // Khóa tài khoản nếu vượt ngưỡng
                if (attempts >= 5) {
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.MINUTE, 15); // khóa 15 phút
                    Date lockedUntil = cal.getTime();
                    user.setAccountLockedUntil(lockedUntil);
            // generate unlock code and send email
            try {
                String rawCode = generateNumericCode(6);
                String hashed = passwordEncoder.encode(rawCode);
                Calendar expCal = Calendar.getInstance();
                expCal.add(Calendar.MINUTE, 15);
                Date expiry = expCal.getTime();
                user.setUnlockTokenHash(hashed);
                user.setUnlockTokenExpiry(expiry);
                user.setUnlockTokenAttempts(0);
                activityLogHelper.logActivity(
                        user.getId(),
                        user.getUsername(),
                        "LOCK_ACCOUNT",
                        "USER",
                        user.getId(),
                        user.getUsername(),
                        String.format("Account locked until %s due to too many failed login attempts", lockedUntil)
                );
                // save before sending email
                userRepository.save(user);
                if (user.getEmail() != null && !user.getEmail().isBlank()) {
                    emailService.sendUnlockCodeEmail(user.getEmail(), rawCode, user.getUsername(), expiry);
                    activityLogHelper.logActivity(user.getId(), user.getUsername(), "SEND_UNLOCK_CODE", "USER", user.getId(), user.getUsername(), "Sent unlock code to email");
                }
            } catch (Exception e) {
                // fallback: still log lock
                activityLogHelper.logActivity(
                        user.getId(),
                        user.getUsername(),
                        "LOCK_ACCOUNT",
                        "USER",
                        user.getId(),
                        user.getUsername(),
                        String.format("Account locked until %s due to too many failed login attempts (email send failed)", lockedUntil)
                );
            }
                }

                user.setUpdatedAt(new Date());
                userRepository.save(user);
            }

            throw new BadRequestException("Sai tên đăng nhập hoặc mật khẩu");
        }
    }

    @Override
    @Transactional
    public TokenPairResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken rt = refreshTokenService.validateRefreshToken(request.getRefreshToken());
        AdUser user = rt.getUser();

        // Rotate refresh token
        refreshTokenService.revokeRefreshToken(rt.getToken());
        RefreshToken newRt = refreshTokenService.createRefreshToken(user.getId());

        // Build new access token based on current roles/permissions in DB
        org.springframework.security.core.userdetails.User userDetails =
                new org.springframework.security.core.userdetails.User(
                        user.getUsername(),
                        user.getPassword(),
                        user.getRoles() != null
                                ? user.getRoles().stream()
                                .map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority(r.getRoleCode()))
                                .toList()
                                : List.of()
                );

        String newAccessToken = jwtService.generateAccessToken(userDetails);
        return new TokenPairResponse(newAccessToken, newRt.getToken());
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail();
        // Always return success to avoid user enumeration
        AdUser user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return;
        }

        String token = generateOpaqueToken();
        Date now = new Date();
        // 15 minutes expiry
        Date exp = new Date(now.getTime() + 15L * 60L * 1000L);
        user.setPasswordResetToken(token);
        user.setPasswordResetTokenExpiry(exp);
        user.setUpdatedAt(now);
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), token, user.getUsername());

        activityLogHelper.logActivity(
                user.getId(),
                user.getUsername(),
                "FORGOT_PASSWORD",
                "USER",
                user.getId(),
                user.getUsername(),
                "Password reset requested"
        );
    }

    @Override
    @Transactional
    public void resetPasswordWithToken(ResetPasswordWithTokenRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Mật khẩu mới và xác nhận mật khẩu không khớp");
        }

        AdUser user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Token đặt lại mật khẩu không hợp lệ"));

        if (user.getPasswordResetTokenExpiry() == null || user.getPasswordResetTokenExpiry().before(new Date())) {
            throw new BadRequestException("Token đặt lại mật khẩu đã hết hạn");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        user.setFailedLoginAttempts(0);
        user.setAccountLockedUntil(null);
        user.setUpdatedAt(new Date());
        userRepository.save(user);

        // revoke all refresh tokens for safety
        refreshTokenService.revokeAllUserTokens(user.getId());
        // blacklist current access token if provided (optional)
        String accessToken = getBearerTokenFromRequest();
        if (accessToken != null) {
            tokenBlacklistService.blacklistToken(accessToken, user.getId(), "PASSWORD_RESET");
        }

        activityLogHelper.logActivity(
                user.getId(),
                user.getUsername(),
                "RESET_PASSWORD_WITH_TOKEN",
                "USER",
                user.getId(),
                user.getUsername(),
                "Password reset via token"
        );
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        AdUser user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new BadRequestException("Token xác thực email không hợp lệ"));

        if (user.getEmailVerificationTokenExpiry() == null || user.getEmailVerificationTokenExpiry().before(new Date())) {
            throw new BadRequestException("Token xác thực email đã hết hạn");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);
        user.setUpdatedAt(new Date());
        userRepository.save(user);

        activityLogHelper.logActivity(
                user.getId(),
                user.getUsername(),
                "VERIFY_EMAIL",
                "USER",
                user.getId(),
                user.getUsername(),
                "Email verified"
        );
    }

    @Override
    @Transactional
    public void resendVerificationEmail(String username) {
        AdUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return; // avoid enumeration
        }
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BadRequestException("Tài khoản chưa có email để xác thực");
        }

        String token = generateOpaqueToken();
        Date now = new Date();
        Date exp = new Date(now.getTime() + 24L * 60L * 60L * 1000L);
        user.setEmailVerificationToken(token);
        user.setEmailVerificationTokenExpiry(exp);
        user.setUpdatedAt(now);
        userRepository.save(user);

        emailService.sendEmailVerification(user.getEmail(), token, user.getUsername());

        activityLogHelper.logActivity(
                user.getId(),
                user.getUsername(),
                "RESEND_VERIFY_EMAIL",
                "USER",
                user.getId(),
                user.getUsername(),
                "Resent verification email"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileDto getCurrentUserProfile(String username) {
        AdUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));
        return UserProfileDto.fromEntity(user);
    }

    @Override
    @Transactional
    public UserProfileDto updateProfile(String username, UpdateProfileRequest request) {
        AdUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));

        // Cập nhật các trường có thể chỉnh sửa
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getAddress() != null) {
            user.setAddress(request.getAddress());
        }
        if (request.getProvince() != null) {
            user.setProvince(request.getProvince());
        }
        if (request.getDistrict() != null) {
            user.setDistrict(request.getDistrict());
        }
        if (request.getWard() != null) {
            user.setWard(request.getWard());
        }
        if (request.getCountry() != null) {
            user.setCountry(request.getCountry());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }

        user.setUpdatedAt(new Date());
        user = userRepository.save(user);

        return UserProfileDto.fromEntity(user);
    }

    @Override
    @Transactional
    public void deleteAccount(String username) {
        AdUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));
        
        // Thay vì xóa, disable tài khoản để giữ lại dữ liệu
        user.setActive(false);
        user.setUpdatedAt(new Date());
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        AdUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));

        // Validate new password matches confirm password
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Mật khẩu mới và xác nhận mật khẩu không khớp");
        }

        // Verify old password
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BadRequestException("Mật khẩu cũ không đúng");
        }

        // Check if new password is same as old password
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("Mật khẩu mới phải khác mật khẩu cũ");
        }

        // Hash and save new password
        String hashedPassword = passwordEncoder.encode(request.getNewPassword());
        user.setPassword(hashedPassword);
        user.setUpdatedAt(new Date());
        userRepository.save(user);

        // Log activity
        activityLogHelper.logActivity(
                user.getId(),
                user.getUsername(),
                "CHANGE_PASSWORD",
                "USER",
                user.getId(),
                user.getUsername(),
                "User changed password successfully"
        );

        // revoke refresh tokens & blacklist current access token if present
        refreshTokenService.revokeAllUserTokens(user.getId());
        String accessToken = getBearerTokenFromRequest();
        if (accessToken != null) {
            tokenBlacklistService.blacklistToken(accessToken, user.getId(), "CHANGE_PASSWORD");
        }
    }

    /**
     * Get client IP address from request
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    // Take the first IP if there are multiple (proxy chain)
                    return xForwardedFor.split(",")[0].trim();
                }
                String xRealIp = request.getHeader("X-Real-IP");
                if (xRealIp != null && !xRealIp.isEmpty()) {
                    return xRealIp;
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            // Ignore
        }
        return "unknown";
    }

    @Override
    @Transactional
    public void logout(String username) {
        AdUser user = userRepository.findByUsername(username).orElse(null);
        
        if (user != null) {
            // Blacklist current access token (if any) + revoke refresh tokens
            String accessToken = getBearerTokenFromRequest();
            if (accessToken != null) {
                tokenBlacklistService.blacklistToken(accessToken, user.getId(), "LOGOUT");
            }
            refreshTokenService.revokeAllUserTokens(user.getId());

            // Log logout activity
            activityLogHelper.logActivity(
                    user.getId(),
                    user.getUsername(),
                    "LOGOUT",
                    "USER",
                    user.getId(),
                    user.getUsername(),
                    "User logged out successfully"
            );
        }
    }

    private String getBearerTokenFromRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return authHeader.substring(7);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String generateOpaqueToken() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    // generate numeric code used for unlock
    private String generateNumericCode(int digits) {
        SecureRandom sr = new SecureRandom();
        int max = (int) Math.pow(10, digits);
        int min = max / 10;
        int n = sr.nextInt(max - min) + min;
        return String.format("%0" + digits + "d", n);
    }

    @Override
    @Transactional
    public void verifyUnlockCode(String username, String code) {
        AdUser user = userRepository.findByUsername(username).orElseThrow(() -> new BadRequestException("Yêu cầu không hợp lệ"));

        if (user.getUnlockTokenExpiry() == null || user.getUnlockTokenExpiry().before(new Date()) || user.getUnlockTokenHash() == null) {
            throw new BadRequestException("Mã mở khóa không hợp lệ hoặc đã hết hạn");
        }

        int attempts = user.getUnlockTokenAttempts() != null ? user.getUnlockTokenAttempts() : 0;
        if (attempts >= 5) {
            user.setUnlockTokenHash(null);
            user.setUnlockTokenExpiry(null);
            user.setUnlockTokenAttempts(0);
            userRepository.save(user);
            activityLogHelper.logActivity(user.getId(), user.getUsername(), "VERIFY_UNLOCK_FAILED", "USER", user.getId(), user.getUsername(), "Exceeded unlock verify attempts");
            throw new BadRequestException("Quá nhiều lần thử mã mở khóa");
        }

        boolean ok = false;
        try {
            ok = passwordEncoder.matches(code, user.getUnlockTokenHash());
        } catch (Exception e) {
            ok = false;
        }

        if (!ok) {
            user.setUnlockTokenAttempts(attempts + 1);
            userRepository.save(user);
            activityLogHelper.logActivity(user.getId(), user.getUsername(), "VERIFY_UNLOCK_FAILED", "USER", user.getId(), user.getUsername(), "Incorrect unlock code");
            throw new BadRequestException("Mã không đúng");
        }

        // success: clear lock + token + revoke sessions
        user.setAccountLockedUntil(null);
        user.setFailedLoginAttempts(0);
        user.setUnlockTokenHash(null);
        user.setUnlockTokenExpiry(null);
        user.setUnlockTokenAttempts(0);
        user.setUpdatedAt(new Date());
        userRepository.save(user);

        refreshTokenService.revokeAllUserTokens(user.getId());
        activityLogHelper.logActivity(user.getId(), user.getUsername(), "UNLOCK_ACCOUNT", "USER", user.getId(), user.getUsername(), "Account unlocked via email code");
    }
}