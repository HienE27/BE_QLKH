package com.example.auth_service.controller;

import com.example.auth_service.common.ApiResponse;
import com.example.auth_service.dto.ActivityLogDto;
import com.example.auth_service.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal/activity-logs")
public class InternalActivityLogController {

    private final ActivityLogService activityLogService;

    public InternalActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @Value("${ACTIVITY_LOG_SERVICE_TOKEN:}")
    private String activityLogServiceToken;

    @PostMapping
    public ApiResponse<ActivityLogDto> createInternal(@RequestBody ActivityLogDto logDto, HttpServletRequest request) {
        String tokenHeader = request.getHeader("X-Activity-Log-Token");
        boolean tokenProvided = activityLogServiceToken != null && !activityLogServiceToken.isBlank();
        boolean tokenValid = tokenProvided && tokenHeader != null && tokenHeader.equals(activityLogServiceToken);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean userAuthenticated = auth != null && auth.isAuthenticated() && auth.getPrincipal() != null && !"anonymousUser".equals(auth.getPrincipal());

        System.out.println("[InternalActivityLogController] tokenHeaderPresent=" + (tokenHeader != null) + ", tokenConfigured=" + tokenProvided + ", tokenValid=" + tokenValid + ", userAuthenticated=" + userAuthenticated);

        if (!tokenValid && !userAuthenticated) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        // Defensive validation to avoid DB constraint errors (username/userId required)
        if (logDto == null) {
            System.out.println("[InternalActivityLogController] payload is null");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload required");
        }

        // Ensure username/displayName presence to avoid DB constraint errors
        if (logDto.getUsername() == null || logDto.getUsername().trim().isEmpty()) {
            if (logDto.getUserId() != null) {
                logDto.setUsername("user-" + logDto.getUserId());
                System.out.println("[InternalActivityLogController] username missing, fallback to user-" + logDto.getUserId());
            } else {
                System.out.println("[InternalActivityLogController] missing username and userId in payload: " + logDto);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username or userId required");
            }
        }

        if (logDto.getDisplayName() == null || logDto.getDisplayName().trim().isEmpty()) {
            // Try to set displayName from username if possible (simple heuristic)
            logDto.setDisplayName(logDto.getUsername());
            System.out.println("[InternalActivityLogController] displayName missing, fallback to username: " + logDto.getUsername());
        }

        // log payload for debugging
        System.out.println("[InternalActivityLogController] incoming internal activity log payload: " + logDto);

        ActivityLogDto created = activityLogService.createActivityLog(logDto);
        return ApiResponse.ok(created);
    }
}


