package com.example.auth_service.dto;

import com.example.auth_service.entity.ActivityLog;
import lombok.Data;

import java.util.Date;

@Data
public class ActivityLogDto {
    private Long id;
    private Long userId;
    private String username;
    private String displayName;
    private String action;
    private String actionLabel;
    private String resourceType;
    private Long resourceId;
    private String resourceName;
    private String details;
    private String ipAddress;
    private String userAgent;
    private Date createdAt;

    public static ActivityLogDto fromEntity(ActivityLog log) {
        ActivityLogDto dto = new ActivityLogDto();
        dto.setId(log.getId());
        dto.setUserId(log.getUserId());
        dto.setUsername(log.getUsername());
        dto.setDisplayName(log.getDisplayName());
        dto.setAction(log.getAction());
        dto.setActionLabel(mapActionLabel(log.getAction(), log.getResourceName()));
        dto.setResourceType(log.getResourceType());
        dto.setResourceId(log.getResourceId());
        dto.setResourceName(log.getResourceName());
        dto.setDetails(log.getDetails());
        dto.setIpAddress(log.getIpAddress());
        dto.setUserAgent(log.getUserAgent());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }

    private static String mapActionLabel(String action, String resourceName) {
        if (action == null) return "-";
        return switch (action) {
            case "LOGIN" -> "Đăng nhập";
            case "LOGOUT" -> "Đăng xuất";
            case "CREATE_USER" -> "Tạo thành viên: " + (resourceName != null ? resourceName : "-");
            case "UPDATE_USER" -> "Cập nhật thành viên: " + (resourceName != null ? resourceName : "-");
            case "DELETE_USER" -> "Xóa thành viên: " + (resourceName != null ? resourceName : "-");
            case "CREATE_ROLE" -> "Tạo vai trò: " + (resourceName != null ? resourceName : "-");
            case "UPDATE_ROLE" -> "Cập nhật vai trò: " + (resourceName != null ? resourceName : "-");
            case "DELETE_ROLE" -> "Xóa vai trò: " + (resourceName != null ? resourceName : "-");
            case "CREATE_PERMISSION" -> "Tạo permission: " + (resourceName != null ? resourceName : "-");
            case "UPDATE_PERMISSION" -> "Cập nhật permission: " + (resourceName != null ? resourceName : "-");
            case "DELETE_PERMISSION" -> "Xóa permission: " + (resourceName != null ? resourceName : "-");
            case "CREATE_RECEIPT" -> "Tạo phiếu nhập: " + (resourceName != null ? resourceName : "-");
            case "UPDATE_RECEIPT" -> "Cập nhật phiếu nhập: " + (resourceName != null ? resourceName : "-");
            case "APPROVE_RECEIPT" -> "Duyệt phiếu nhập: " + (resourceName != null ? resourceName : "-");
            case "CONFIRM_RECEIPT" -> "Xác nhận nhập kho: " + (resourceName != null ? resourceName : "-");
            case "CANCEL_RECEIPT" -> "Hủy phiếu nhập: " + (resourceName != null ? resourceName : "-");
            case "REJECT_RECEIPT" -> "Từ chối phiếu nhập: " + (resourceName != null ? resourceName : "-");
            case "DELETE_RECEIPT" -> "Xóa phiếu nhập: " + (resourceName != null ? resourceName : "-");
            case "CREATE_DELIVERY" -> "Tạo phiếu xuất: " + (resourceName != null ? resourceName : "-");
            case "UPDATE_DELIVERY" -> "Cập nhật phiếu xuất: " + (resourceName != null ? resourceName : "-");
            case "APPROVE_DELIVERY" -> "Duyệt phiếu xuất: " + (resourceName != null ? resourceName : "-");
            case "CONFIRM_DELIVERY" -> "Xác nhận xuất kho: " + (resourceName != null ? resourceName : "-");
            case "CANCEL_DELIVERY" -> "Hủy phiếu xuất: " + (resourceName != null ? resourceName : "-");
            case "REJECT_DELIVERY" -> "Từ chối phiếu xuất: " + (resourceName != null ? resourceName : "-");
            case "DELETE_DELIVERY" -> "Xóa phiếu xuất: " + (resourceName != null ? resourceName : "-");
            case "CREATE_INVENTORY_CHECK" -> "Tạo kiểm kê: " + (resourceName != null ? resourceName : "-");
            case "UPDATE_INVENTORY_CHECK" -> "Cập nhật kiểm kê: " + (resourceName != null ? resourceName : "-");
            case "APPROVE_INVENTORY_CHECK" -> "Duyệt kiểm kê: " + (resourceName != null ? resourceName : "-");
            case "CONFIRM_INVENTORY_CHECK" -> "Xác nhận kiểm kê: " + (resourceName != null ? resourceName : "-");
            case "REJECT_INVENTORY_CHECK" -> "Từ chối kiểm kê: " + (resourceName != null ? resourceName : "-");
            case "DELETE_INVENTORY_CHECK" -> "Xóa kiểm kê: " + (resourceName != null ? resourceName : "-");
            case "CREATE_ORDER" -> "Tạo đơn hàng: " + (resourceName != null ? resourceName : "-");
            case "UPDATE_ORDER" -> "Cập nhật đơn hàng: " + (resourceName != null ? resourceName : "-");
            case "APPROVE_ORDER" -> "Duyệt đơn hàng: " + (resourceName != null ? resourceName : "-");
            case "CONFIRM_ORDER" -> "Xác nhận đơn hàng: " + (resourceName != null ? resourceName : "-");
            case "CANCEL_ORDER" -> "Hủy đơn hàng: " + (resourceName != null ? resourceName : "-");
            case "DELETE_ORDER" -> "Xóa đơn hàng: " + (resourceName != null ? resourceName : "-");
            default -> action;
        };
    }
}

