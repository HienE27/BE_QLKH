# Activity Logs - Tài liệu kỹ thuật

## Tổng quan

Hệ thống Activity Logs (Nhật ký hoạt động) ghi lại tất cả các hoạt động quan trọng của người dùng trong hệ thống, bao gồm đăng nhập, quản lý user, role, permission, v.v.

## Kiến trúc

### Backend (Spring Boot)

#### Entities
- **ActivityLog**: Entity chính lưu trữ thông tin log
  - `log_id`: ID tự động
  - `user_id`: ID người dùng thực hiện hành động
  - `username`: Tên đăng nhập
  - `action`: Loại hành động (LOGIN, CREATE_USER, UPDATE_USER, etc.)
  - `resource_type`: Loại tài nguyên (USER, ROLE, PERMISSION)
  - `resource_id`: ID của tài nguyên
  - `resource_name`: Tên của tài nguyên
  - `details`: Chi tiết bổ sung (JSON string)
  - `ip_address`: Địa chỉ IP
  - `user_agent`: User Agent string
  - `created_at`: Thời gian tạo

#### Services
- **ActivityLogService**: Interface định nghĩa các operations
  - `searchActivityLogs()`: Tìm kiếm logs với filters
  - `getActivityLogById()`: Lấy log theo ID
  - `deleteActivityLog()`: Xóa một log
  - `deleteActivityLogsBulk()`: Xóa nhiều logs
  - `getStatistics()`: Lấy thống kê

- **ActivityLogHelper**: Utility class tự động log activities
  - `logActivity()`: Tự động log với thông tin user hiện tại
  - Tự động lấy IP và User Agent từ request
  - Sử dụng transaction riêng để đảm bảo logs được lưu

#### Controllers
- **ActivityLogController**: REST API endpoints
  - `GET /api/activity-logs`: Tìm kiếm logs (với filters)
  - `GET /api/activity-logs/{id}`: Lấy log theo ID
  - `GET /api/activity-logs/user/{userId}`: Lấy logs của user
  - `GET /api/activity-logs/statistics`: Lấy thống kê
  - `DELETE /api/activity-logs/{id}`: Xóa một log (yêu cầu ADMIN)
  - `DELETE /api/activity-logs/bulk`: Xóa nhiều logs (yêu cầu ADMIN)

### Frontend (Next.js + React)

#### Components
- **Activity Logs Page**: Trang chính hiển thị và quản lý logs
  - Filter section với các bộ lọc cơ bản và nâng cao
  - Statistics panel hiển thị thống kê
  - Virtual table với pagination
  - Modal chi tiết
  - Bulk selection và delete

#### Services
- **activity-log.service.ts**: API client
  - `searchActivityLogs()`: Tìm kiếm
  - `getActivityLogById()`: Lấy chi tiết
  - `deleteActivityLog()`: Xóa một log
  - `deleteActivityLogsBulk()`: Xóa nhiều logs
  - `getActivityLogStatistics()`: Lấy thống kê

## Các loại Actions

### Authentication
- `LOGIN`: Đăng nhập
- `LOGOUT`: Đăng xuất

### User Management
- `CREATE_USER`: Tạo user mới
- `UPDATE_USER`: Cập nhật user
- `DELETE_USER`: Xóa user
- `RESET_PASSWORD`: Đặt lại mật khẩu
- `UPDATE_USER_PERMISSIONS`: Cập nhật phân quyền trực tiếp của user

### Role Management
- `CREATE_ROLE`: Tạo role mới
- `UPDATE_ROLE`: Cập nhật role
- `DELETE_ROLE`: Xóa role
- `UPDATE_ROLE_PERMISSIONS`: Cập nhật phân quyền của role

### Permission Management
- `CREATE_PERMISSION`: Tạo permission mới
- `UPDATE_PERMISSION`: Cập nhật permission
- `DELETE_PERMISSION`: Xóa permission

### Receipt / Import (Phiếu nhập)
- `CREATE_RECEIPT`: Tạo phiếu nhập
- `UPDATE_RECEIPT`: Cập nhật phiếu nhập
- `APPROVE_RECEIPT`: Duyệt phiếu nhập
- `CONFIRM_RECEIPT`: Xác nhận nhập kho (đã nhập)
- `CANCEL_RECEIPT`: Hủy phiếu nhập
- `REJECT_RECEIPT`: Từ chối phiếu nhập
- `DELETE_RECEIPT`: Xóa phiếu nhập

### Delivery / Export (Phiếu xuất)
- `CREATE_DELIVERY`: Tạo phiếu xuất
- `UPDATE_DELIVERY`: Cập nhật phiếu xuất
- `APPROVE_DELIVERY`: Duyệt phiếu xuất
- `CONFIRM_DELIVERY`: Xác nhận xuất kho (đã xuất)
- `CANCEL_DELIVERY`: Hủy phiếu xuất
- `REJECT_DELIVERY`: Từ chối phiếu xuất
- `DELETE_DELIVERY`: Xóa phiếu xuất

### Inventory Check (Kiểm kê)
- `CREATE_INVENTORY_CHECK`: Tạo phiếu kiểm kê
- `UPDATE_INVENTORY_CHECK`: Cập nhật phiếu kiểm kê
- `APPROVE_INVENTORY_CHECK`: Duyệt kiểm kê
- `CONFIRM_INVENTORY_CHECK`: Xác nhận kiểm kê
- `REJECT_INVENTORY_CHECK`: Từ chối kiểm kê
- `DELETE_INVENTORY_CHECK`: Xóa kiểm kê

### Order (Đơn hàng)
- `CREATE_ORDER`: Tạo đơn hàng
- `UPDATE_ORDER`: Cập nhật đơn hàng
- `APPROVE_ORDER`: Duyệt đơn hàng
- `CONFIRM_ORDER`: Xác nhận đơn hàng
- `CANCEL_ORDER`: Hủy đơn hàng
- `DELETE_ORDER`: Xóa đơn hàng

## Security

### Permissions
- **Xem logs**: Tất cả user đã đăng nhập
- **Xóa logs**: Chỉ ADMIN hoặc user có permission `DELETE_ACTIVITY_LOG`

### Implementation
```java
@DeleteMapping("/{id}")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('DELETE_ACTIVITY_LOG')")
public ApiResponse<String> deleteActivityLog(@PathVariable Long id) {
    // ...
}
```

## Auto-logging

Hệ thống tự động log các hoạt động thông qua `ActivityLogHelper`:

```java
// Trong UserServiceImpl
activityLogHelper.logActivity(
    "CREATE_USER",
    "USER",
    savedUser.getId(),
    savedUser.getUsername(),
    String.format("Created new user: %s", savedUser.getUsername())
);
```

### Tích hợp
- **UserService**: CREATE_USER, UPDATE_USER, DELETE_USER, RESET_PASSWORD, UPDATE_USER_PERMISSIONS
- **RoleService**: CREATE_ROLE, UPDATE_ROLE, DELETE_ROLE, UPDATE_ROLE_PERMISSIONS
- **PermissionService**: CREATE_PERMISSION, UPDATE_PERMISSION, DELETE_PERMISSION
- **AuthService**: LOGIN

## API Endpoints

### GET /api/activity-logs
Tìm kiếm logs với filters

**Query Parameters:**
- `userId` (optional): Filter theo user ID
- `action` (optional): Filter theo action
- `startDate` (optional): Từ ngày (ISO date format)
- `endDate` (optional): Đến ngày (ISO date format)
- `ipAddress` (optional): Filter theo IP (partial match)
- `userAgent` (optional): Filter theo User Agent (partial match)
- `page` (default: 0): Số trang
- `size` (default: 10): Số items mỗi trang

**Response:**
```json
{
  "success": true,
  "data": {
    "content": [...],
    "totalElements": 100,
    "totalPages": 10,
    "number": 0,
    "size": 10
  }
}
```

### GET /api/activity-logs/{id}
Lấy chi tiết một log

### GET /api/activity-logs/statistics
Lấy thống kê

**Query Parameters:**
- `startDate` (optional): Từ ngày
- `endDate` (optional): Đến ngày

**Response:**
```json
{
  "success": true,
  "data": {
    "totalLogs": 1000,
    "totalUsers": 50,
    "actionCounts": {
      "LOGIN": 500,
      "CREATE_USER": 100,
      ...
    },
    "topUsers": {
      "admin": 200,
      "manager": 150,
      ...
    },
    "todayLogs": 50,
    "weekLogs": 300,
    "monthLogs": 1000
  }
}
```

### DELETE /api/activity-logs/{id}
Xóa một log (yêu cầu ADMIN)

### DELETE /api/activity-logs/bulk
Xóa nhiều logs (yêu cầu ADMIN)

**Request Body:**
```json
[1, 2, 3, 4, 5]
```

## Frontend Features

### Filters
- **Cơ bản**: User, Action, Date range
- **Nâng cao**: IP Address, User Agent (có thể ẩn/hiện)

### Statistics Panel
- Tổng số logs
- Số lượng users
- Logs hôm nay / 7 ngày / 30 ngày
- Top actions
- Top users

### Bulk Operations
- Chọn nhiều logs bằng checkbox
- Xóa hàng loạt
- Select all / Deselect all

### Export
- **Excel**: Xuất ra file .xlsx
- **CSV**: Xuất ra file .csv
- **PDF**: Xuất ra file .pdf (sử dụng jsPDF)

### Detail Modal
Hiển thị đầy đủ thông tin:
- ID, Thời gian, User
- Action với badge màu
- Resource type và name
- Details
- IP Address (monospace font)
- User Agent (break-all để hiển thị đầy đủ)

## Database Schema

```sql
CREATE TABLE `activity_logs` (
  `log_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `username` VARCHAR(191) NOT NULL,
  `action` VARCHAR(100) NOT NULL,
  `resource_type` VARCHAR(100) DEFAULT NULL,
  `resource_id` BIGINT DEFAULT NULL,
  `resource_name` VARCHAR(255) DEFAULT NULL,
  `details` TEXT DEFAULT NULL,
  `ip_address` VARCHAR(45) DEFAULT NULL,
  `user_agent` VARCHAR(500) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`log_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_action` (`action`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_resource` (`resource_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Best Practices

1. **Transaction Management**: Logs được lưu trong transaction riêng để không ảnh hưởng đến business logic
2. **Error Handling**: Logging errors không throw exception để không làm gián đoạn flow
3. **Performance**: Sử dụng indexes cho các cột thường query
4. **Security**: Chỉ ADMIN mới có thể xóa logs
5. **Data Retention**: Có thể implement auto-cleanup cho logs cũ (ví dụ > 90 ngày)

## Future Enhancements

1. **Auto-cleanup**: Tự động xóa logs cũ
2. **Real-time notifications**: Thông báo khi có hoạt động quan trọng
3. **Advanced analytics**: Phân tích hành vi người dùng
4. **Export formats**: Thêm XML, JSON export
5. **Audit trail**: Track changes to logs themselves

