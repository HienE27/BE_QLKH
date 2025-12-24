package com.example.auth_service.service.impl;

import com.example.auth_service.dto.ActivityLogDto;
import com.example.auth_service.dto.ActivityLogStatisticsDto;
import com.example.auth_service.entity.ActivityLog;
import com.example.auth_service.exception.NotFoundException;
import com.example.auth_service.repository.ActivityLogRepository;
import com.example.auth_service.service.ActivityLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Calendar;
import java.util.Date;

@Service
public class ActivityLogServiceImpl implements ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogServiceImpl(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    @Override
    public Page<ActivityLogDto> searchActivityLogs(Long userId, String action, Date startDate, Date endDate, String ipAddress, String userAgent, String keyword, Pageable pageable) {
        Page<ActivityLog> logs = activityLogRepository.searchActivityLogs(userId, action, startDate, endDate, ipAddress, userAgent, keyword, pageable);
        return logs.map(ActivityLogDto::fromEntity);
    }

    @Override
    @Transactional
    public ActivityLogDto createActivityLog(ActivityLogDto logDto) {
        ActivityLog log = new ActivityLog();
        log.setUserId(logDto.getUserId());
        log.setUsername(logDto.getUsername());
        log.setDisplayName(logDto.getDisplayName());
        log.setAction(logDto.getAction());
        log.setResourceType(logDto.getResourceType());
        log.setResourceId(logDto.getResourceId());
        log.setResourceName(logDto.getResourceName());
        log.setDetails(logDto.getDetails());
        log.setIpAddress(logDto.getIpAddress());
        log.setUserAgent(logDto.getUserAgent());
        log.setCreatedAt(new Date());

        ActivityLog savedLog = activityLogRepository.save(log);
        return ActivityLogDto.fromEntity(savedLog);
    }

    @Override
    public Page<ActivityLogDto> getActivityLogsByUserId(Long userId, Pageable pageable) {
        Page<ActivityLog> logs = activityLogRepository.findByUserId(userId, pageable);
        return logs.map(ActivityLogDto::fromEntity);
    }

    @Override
    public ActivityLogDto getActivityLogById(Long id) {
        ActivityLog log = activityLogRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Activity log not found with id: " + id));
        return ActivityLogDto.fromEntity(log);
    }

    @Override
    @Transactional
    public void deleteActivityLog(Long id) {
        ActivityLog log = activityLogRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Activity log not found with id: " + id));
        activityLogRepository.delete(log);
    }

    @Override
    @Transactional
    public void deleteActivityLogsBulk(List<Long> ids) {
        List<ActivityLog> logs = activityLogRepository.findAllById(ids);
        if (logs.size() != ids.size()) {
            throw new NotFoundException("Some activity logs not found");
        }
        activityLogRepository.deleteAll(logs);
    }

    @Override
    public ActivityLogStatisticsDto getStatistics(Date startDate, Date endDate) {
        List<ActivityLog> allLogs;
        
        if (startDate != null || endDate != null) {
            // Filter by date range
            allLogs = activityLogRepository.findAll().stream()
                    .filter(log -> {
                        if (startDate != null && log.getCreatedAt().before(startDate)) {
                            return false;
                        }
                        if (endDate != null) {
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(endDate);
                            cal.add(Calendar.DAY_OF_MONTH, 1);
                            Date endDatePlusOne = cal.getTime();
                            if (log.getCreatedAt().after(endDatePlusOne)) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
        } else {
            allLogs = activityLogRepository.findAll();
        }

        // Calculate statistics
        long totalLogs = allLogs.size();
        
        // Count unique users
        long totalUsers = allLogs.stream()
                .map(ActivityLog::getUserId)
                .distinct()
                .count();

        // Count by action
        Map<String, Long> actionCounts = allLogs.stream()
                .collect(Collectors.groupingBy(
                        ActivityLog::getAction,
                        Collectors.counting()
                ));

        // Top users by activity count
        Map<String, Long> topUsers = allLogs.stream()
                .collect(Collectors.groupingBy(
                        ActivityLog::getUsername,
                        Collectors.counting()
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        // Today's logs
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        Date todayStart = today.getTime();
        
        long todayLogs = allLogs.stream()
                .filter(log -> log.getCreatedAt().after(todayStart) || log.getCreatedAt().equals(todayStart))
                .count();

        // Week's logs
        Calendar weekAgo = Calendar.getInstance();
        weekAgo.add(Calendar.DAY_OF_MONTH, -7);
        Date weekStart = weekAgo.getTime();
        
        long weekLogs = allLogs.stream()
                .filter(log -> log.getCreatedAt().after(weekStart) || log.getCreatedAt().equals(weekStart))
                .count();

        // Month's logs
        Calendar monthAgo = Calendar.getInstance();
        monthAgo.add(Calendar.MONTH, -1);
        Date monthStart = monthAgo.getTime();
        
        long monthLogs = allLogs.stream()
                .filter(log -> log.getCreatedAt().after(monthStart) || log.getCreatedAt().equals(monthStart))
                .count();

        return new ActivityLogStatisticsDto(
                totalLogs,
                totalUsers,
                actionCounts,
                topUsers,
                todayLogs,
                weekLogs,
                monthLogs
        );
    }

    @Override
    @Transactional
    @Scheduled(cron = "0 0 2 * * ?") // chạy mỗi ngày lúc 2h sáng
    public void cleanupOldLogs() {
        Calendar cal = Calendar.getInstance();
        // TODO: sau này có thể lấy retentionDays từ cấu hình
        cal.add(Calendar.DAY_OF_MONTH, -90); // giữ lại 90 ngày
        Date threshold = cal.getTime();

        int deleted = activityLogRepository.deleteByCreatedAtBefore(threshold);
        System.out.println("ActivityLogServiceImpl.cleanupOldLogs - deleted " + deleted + " old activity logs");
    }
}

