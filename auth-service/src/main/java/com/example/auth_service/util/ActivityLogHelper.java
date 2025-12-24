package com.example.auth_service.util;

import com.example.auth_service.entity.ActivityLog;
import com.example.auth_service.repository.ActivityLogRepository;
import com.example.auth_service.repository.AdUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Date;

@Component
public class ActivityLogHelper {

    private final ActivityLogRepository activityLogRepository;
    private final AdUserRepository userRepository;

    public ActivityLogHelper(ActivityLogRepository activityLogRepository, AdUserRepository userRepository) {
        this.activityLogRepository = activityLogRepository;
        this.userRepository = userRepository;
    }

    /**
     * Tự động log activity với thông tin user hiện tại
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logActivity(String action, String resourceType, Long resourceId, String resourceName, String details) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
                // Không log nếu không có user authenticated
                System.err.println("ActivityLogHelper: No authenticated user found for action: " + action);
                return;
            }

            String username = auth.getName();
            Long userId = getUserIdFromUsername(username);
            
            if (userId == null) {
                System.err.println("ActivityLogHelper: Could not find userId for username: " + username);
                return;
            }

            // Lấy IP và User-Agent từ request
            String ipAddress = getClientIpAddress();
            String userAgent = getUserAgent();

            ActivityLog log = new ActivityLog();
            log.setUserId(userId);
            log.setUsername(username);
            log.setAction(action);
            log.setResourceType(resourceType);
            log.setResourceId(resourceId);
            log.setResourceName(resourceName);
            // translate and normalize details to Vietnamese for storage/display
            log.setDetails(translateDetails(details, action, resourceName));
            // try to set displayName from user repository (first+last) if available
            try {
                userRepository.findByUsername(username).ifPresent(u -> {
                    String full = ((u.getFirstName() != null ? u.getFirstName().trim() : "") + " " + (u.getLastName() != null ? u.getLastName().trim() : "")).trim();
                    if (full.isEmpty()) full = username;
                    log.setDisplayName(full);
                });
            } catch (Exception ignore) {
                log.setDisplayName(username);
            }
            log.setIpAddress(ipAddress);
            log.setUserAgent(userAgent);
            log.setCreatedAt(new Date());

            ActivityLog savedLog = activityLogRepository.save(log);
            activityLogRepository.flush(); // Force flush to database
            System.out.println("ActivityLogHelper: Successfully logged activity - " + action + " by " + username + " (log_id: " + savedLog.getId() + ")");
        } catch (Exception e) {
            // Log error nhưng không throw để không ảnh hưởng đến business logic
            System.err.println("ActivityLogHelper: Failed to log activity: " + action + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Log activity với userId cụ thể (dùng khi không có user authenticated)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logActivity(Long userId, String username, String action, String resourceType, Long resourceId, String resourceName, String details) {
        try {
            String ipAddress = getClientIpAddress();
            String userAgent = getUserAgent();

            ActivityLog log = new ActivityLog();
            log.setUserId(userId);
            log.setUsername(username);
            log.setAction(action);
            log.setResourceType(resourceType);
            log.setResourceId(resourceId);
            log.setResourceName(resourceName);
            log.setDetails(translateDetails(details, action, resourceName));
            log.setIpAddress(ipAddress);
            log.setUserAgent(userAgent);
            log.setCreatedAt(new Date());

            ActivityLog savedLog = activityLogRepository.save(log);
            activityLogRepository.flush(); // Force flush to database
            System.out.println("ActivityLogHelper: Successfully logged activity - " + action + " by " + username + " (log_id: " + savedLog.getId() + ")");
        } catch (Exception e) {
            System.err.println("ActivityLogHelper: Failed to log activity: " + action + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Translate / normalize English detail string into Vietnamese for display.
     * If details is null or empty, produce a sensible default based on action and resourceName.
     */
    private String translateDetails(String details, String action, String resourceName) {
        if (details == null || details.isBlank()) {
            if (action == null) return "-";
            return switch (action) {
                case "LOGIN" -> "Đăng nhập";
                case "LOGOUT" -> "Đăng xuất";
                case "CREATE_USER" -> "Tạo thành viên: " + (resourceName != null ? resourceName : "-");
                case "DELETE_USER" -> "Xóa thành viên: " + (resourceName != null ? resourceName : "-");
                case "CREATE_ROLE" -> "Tạo vai trò: " + (resourceName != null ? resourceName : "-");
                case "DELETE_ROLE" -> "Xóa vai trò: " + (resourceName != null ? resourceName : "-");
                case "UPDATE_ROLE_PERMISSIONS" -> "Cập nhật phân quyền vai trò: " + (resourceName != null ? resourceName : "-");
                case "UPDATE_USER_PERMISSIONS" -> "Cập nhật phân quyền thành viên: " + (resourceName != null ? resourceName : "-");
                case "RESET_PASSWORD", "RESET_PASSWORD_WITH_TOKEN" -> "Đặt lại mật khẩu";
                default -> "-";
            };
        }

        // Simple regex-based replacements for common English messages
        try {
            String out = details;
            out = out.replaceAll("(?i)Created new user:\\s*(.+)", "Tạo thành viên: $1");
            out = out.replaceAll("(?i)Deleted user:\\s*(.+)", "Xóa thành viên: $1");
            out = out.replaceAll("(?i)Created new role:\\s*(.+)", "Tạo vai trò: $1");
            out = out.replaceAll("(?i)Deleted role:\\s*(.+)", "Xóa vai trò: $1");
            out = out.replaceAll("(?i)Updated permissions for role:\\s*(.+)", "Cập nhật phân quyền vai trò: $1");
            out = out.replaceAll("(?i)Updated direct permissions for user:\\s*(.+)", "Cập nhật phân quyền trực tiếp cho thành viên: $1");
            out = out.replaceAll("(?i)Updated user information", "Cập nhật thông tin thành viên");
            out = out.replaceAll("(?i)User logged in successfully", "Đăng nhập");
            out = out.replaceAll("(?i)User logged out", "Đăng xuất");
            out = out.replaceAll("(?i)Failed login attempt", "Đăng nhập thất bại");
            out = out.replaceAll("(?i)Password reset requested", "Yêu cầu đặt lại mật khẩu");
            out = out.replaceAll("(?i)Account locked until (.+) due to too many failed login attempts", "Tài khoản bị khóa đến $1 do quá nhiều lần đăng nhập thất bại");
            return out;
        } catch (Exception e) {
            return details; // fallback to original
        }
    }

    private Long getUserIdFromUsername(String username) {
        return userRepository.findByUsername(username)
                .map(user -> user.getId())
                .orElse(null);
    }

    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
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

    private String getUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String userAgent = request.getHeader("User-Agent");
                if (userAgent != null && userAgent.length() > 500) {
                    return userAgent.substring(0, 500); // Limit to 500 chars
                }
                return userAgent != null ? userAgent : "unknown";
            }
        } catch (Exception e) {
            // Ignore
        }
        return "unknown";
    }
}

