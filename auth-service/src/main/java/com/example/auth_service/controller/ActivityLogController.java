package com.example.auth_service.controller;

import com.example.auth_service.common.ApiResponse;
import com.example.auth_service.dto.ActivityLogDto;
import com.example.auth_service.service.ActivityLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/activity-logs")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    public ActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @Value("${ACTIVITY_LOG_SERVICE_TOKEN:}")
    private String activityLogServiceToken;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    public ApiResponse<com.example.auth_service.dto.PageResponse<ActivityLogDto>> searchActivityLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) String userAgent,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ActivityLogDto> logs = activityLogService.searchActivityLogs(userId, action, startDate, endDate, ipAddress, userAgent, keyword, pageable);
        
        com.example.auth_service.dto.PageResponse<ActivityLogDto> pageResponse = 
            new com.example.auth_service.dto.PageResponse<>(
                logs.getContent(),
                logs.getTotalElements(),
                logs.getTotalPages(),
                logs.getNumber(),
                logs.getSize()
            );
        
        return ApiResponse.ok(pageResponse);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    public ApiResponse<ActivityLogDto> getActivityLogById(@PathVariable Long id) {
        ActivityLogDto log = activityLogService.getActivityLogById(id);
        return ApiResponse.ok(log);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('DELETE_ACTIVITY_LOG')")
    public ApiResponse<String> deleteActivityLog(@PathVariable Long id) {
        activityLogService.deleteActivityLog(id);
        return ApiResponse.ok("Đã xóa nhật ký hoạt động thành công");
    }

    @DeleteMapping("/bulk")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('DELETE_ACTIVITY_LOG')")
    public ApiResponse<String> deleteActivityLogsBulk(@RequestBody List<Long> ids) {
        activityLogService.deleteActivityLogsBulk(ids);
        return ApiResponse.ok("Đã xóa " + ids.size() + " nhật ký hoạt động thành công");
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    public ApiResponse<com.example.auth_service.dto.ActivityLogStatisticsDto> getStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate
    ) {
        com.example.auth_service.dto.ActivityLogStatisticsDto stats = activityLogService.getStatistics(startDate, endDate);
        return ApiResponse.ok(stats);
    }

    @PostMapping
    // Allow services/admins to create activity logs (services should authenticate or present token)
    public ApiResponse<ActivityLogDto> createActivityLog(@RequestBody ActivityLogDto logDto, HttpServletRequest request) {
        String authHeader = request.getHeader("X-Activity-Log-Token");
        boolean tokenProvided = activityLogServiceToken != null && !activityLogServiceToken.isBlank();
        boolean tokenValid = false;
        if (tokenProvided && authHeader != null && authHeader.equals(activityLogServiceToken)) {
            tokenValid = true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean userAuthenticated = false;
        if (auth != null && auth.isAuthenticated()) {
            Object principal = auth.getPrincipal();
            if (principal != null && !"anonymousUser".equals(principal)) {
                userAuthenticated = true;
            }
        }

        System.out.println("[ActivityLogController] tokenHeaderPresent=" + (authHeader != null) + ", tokenConfigured=" + (activityLogServiceToken != null && !activityLogServiceToken.isBlank()) + ", tokenValid=" + tokenValid + ", userAuthenticated=" + userAuthenticated);

        if (!tokenValid && !userAuthenticated) {
            // neither service token nor authenticated user -> forbidden
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        ActivityLogDto created = activityLogService.createActivityLog(logDto);
        return ApiResponse.ok(created);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    public ApiResponse<com.example.auth_service.dto.PageResponse<ActivityLogDto>> getActivityLogsByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ActivityLogDto> logs = activityLogService.getActivityLogsByUserId(userId, pageable);
        
        com.example.auth_service.dto.PageResponse<ActivityLogDto> pageResponse = 
            new com.example.auth_service.dto.PageResponse<>(
                logs.getContent(),
                logs.getTotalElements(),
                logs.getTotalPages(),
                logs.getNumber(),
                logs.getSize()
            );
        
        return ApiResponse.ok(pageResponse);
    }
}

