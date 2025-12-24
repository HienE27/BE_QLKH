package com.example.order_service.service.impl;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.order_service.dto.*;
import com.example.order_service.entity.ShopOrder;
import com.example.order_service.entity.ShopOrderDetail;
import com.example.order_service.exception.NotFoundException;
import com.example.order_service.repository.ShopOrderDetailRepository;
import com.example.order_service.repository.ShopOrderRepository;
import com.example.order_service.service.OrderService;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private final ShopOrderRepository orderRepo;
    private final ShopOrderDetailRepository detailRepo;
    private final WebClient.Builder webClientBuilder;

    public OrderServiceImpl(ShopOrderRepository orderRepo,
            ShopOrderDetailRepository detailRepo,
            WebClient.Builder webClientBuilder) {
        this.orderRepo = orderRepo;
        this.detailRepo = detailRepo;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public OrderDto create(OrderRequest req) {

        // TÍNH SUBTOTAL (tổng tiền hàng)
        double subtotal = 0.0;
        if (req.getDetails() != null) {
            for (OrderDetailRequest d : req.getDetails()) {
                if (d.getUnitPrice() != null && d.getQuantity() != null) {
                    subtotal += d.getUnitPrice().doubleValue() * d.getQuantity();
                }
            }
        }

        // GỌI PROMOTION-SERVICE NẾU CÓ voucherCode
        double discount = 0.0;

        if (req.getVoucherCode() != null && !req.getVoucherCode().isBlank()) {
            try {
                ApiResponse<VoucherDto> res = webClientBuilder.build()
                        .get()
                        .uri("http://promotion-service/api/vouchers/code/{code}", req.getVoucherCode())
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<ApiResponse<VoucherDto>>() {
                        })
                        .block();

                if (res != null && res.isSuccess()
                        && res.getData() != null
                        && res.getData().getDiscountAmount() != null) {
                    discount = res.getData().getDiscountAmount();
                }

            } catch (Exception e) {
                // nếu gọi fail thì coi như không có voucher
                System.out.println("Call promotion-service error: " + e.getMessage());
            }
        }

        // Không cho giảm quá subtotal
        if (discount > subtotal) {
            discount = subtotal;
        }

        // tiền hàng sau giảm
        double itemTotal = subtotal - discount;
        if (itemTotal < 0)
            itemTotal = 0;

        // shipFee (có thể null)
        double shipFeeDouble = req.getShipFee() != null
                ? req.getShipFee().doubleValue()
                : 0.0;

        //  TỔNG TIỀN ĐƠN HÀNG = tiền hàng sau giảm + phí ship
        double finalTotal = itemTotal + shipFeeDouble;

        // 2. lưu order
        ShopOrder order = new ShopOrder();
        order.setCustomerId(req.getCustomerId());
        order.setPaymentTypeId(req.getPaymentTypeId());
        order.setUserId(req.getUserId());
        order.setShipName(req.getShipName());
        order.setShipAddress(req.getShipAddress());
        order.setShipCity(req.getShipCity());
        order.setShipCountry(req.getShipCountry());
        order.setShipPostalCode(req.getShipPostalCode());
        order.setShipFee(req.getShipFee());
        order.setOrderDate(new Date());
        order.setOrderStatus("NEW");
        order.setCreatedAt(new Date());
        order.setUpdatedAt(new Date());

        // LƯU TỔNG TIỀN + GIẢM GIÁ
        order.setTotalAmount(finalTotal);
        order.setDiscountAmount(discount);

        order = orderRepo.save(order);

        // Ghi nhật ký hoạt động bằng cách gọi auth-service
        try {
            String uname = getCurrentUsername();
            if (uname == null) uname = "system";
            System.out.println("Preparing to send activity log: action=CREATE_ORDER, resourceType=ORDER, resourceId=" + order.getId() + ", userId=" + order.getUserId() + ", username=" + uname);
            sendActivityLog(
                    order.getUserId(),
                    uname,
                    "CREATE_ORDER",
                    "ORDER",
                    order.getId(),
                    "ORDER#" + order.getId(),
                    String.format("Created order: %s", order.getId())
            );
        } catch (Exception e) {
            // Không gây hỏng flow nếu log fail
            System.out.println("Failed to send activity log for CREATE_ORDER: " + e.getMessage());
        }

        // 3. lưu chi tiết
        if (req.getDetails() != null) {
            for (OrderDetailRequest d : req.getDetails()) {
                ShopOrderDetail detail = new ShopOrderDetail();
                detail.setOrderId(order.getId());
                detail.setProductId(d.getProductId());
                detail.setQuantity(d.getQuantity());
                detail.setUnitPrice(d.getUnitPrice());
                // nếu sau này muốn phân bổ discount xuống detail thì set thêm ở đây
                detailRepo.save(detail);
            }
        }

        // 3.2 GỌI INVENTORY-SERVICE TẠO PHIẾU XUẤT
        try {
            if (req.getDetails() != null && !req.getDetails().isEmpty()) {

                InventoryExportRequest exportReq = new InventoryExportRequest();
                exportReq.setStoreId(req.getStoreId());
                exportReq.setUserId(req.getUserId());
                exportReq.setOrderId(order.getId());
                exportReq.setNote("Xuất kho cho đơn hàng #" + order.getId());

                List<InventoryExportDetailRequest> exportDetails = req.getDetails().stream()
                        .map(d -> {
                            InventoryExportDetailRequest ed = new InventoryExportDetailRequest();
                            ed.setProductId(d.getProductId());
                            ed.setQuantity(d.getQuantity());
                            ed.setUnitPrice(d.getUnitPrice());
                            return ed;
                        })
                        .toList();
                exportReq.setDetails(exportDetails);

                ApiResponse<?> res = webClientBuilder.build()
                        .post()
                        .uri("http://inventory-service/api/exports")
                        .bodyValue(exportReq)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<ApiResponse<?>>() {
                        })
                        .block();

                if (res == null || !res.isSuccess()) {
                    System.out.println("Tạo phiếu xuất kho thất bại: " +
                            (res != null ? res.getMessage() : "null response"));
                }
            }
        } catch (Exception e) {
            System.out.println("Call inventory-service error: " + e.getMessage());
        }

        // 4. load lại details từ DB
        List<ShopOrderDetail> details = detailRepo.findByOrderId(order.getId());
        return toDto(order, details);
    }

    @Override
    public List<OrderDto> getAll() {
        return orderRepo.findAll().stream()
                .map(o -> {
                    List<ShopOrderDetail> details = detailRepo.findByOrderId(o.getId());
                    return toDto(o, details);
                })
                .toList();
    }

    @Override
    public List<OrderDto> getByCustomer(Long customerId) {
        return orderRepo.findByCustomerId(customerId).stream()
                .map(o -> {
                    List<ShopOrderDetail> details = detailRepo.findByOrderId(o.getId());
                    return toDto(o, details);
                })
                .toList();
    }

    @Override
    public OrderDto getById(Long id) {
        ShopOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found: " + id));

        List<ShopOrderDetail> details = detailRepo.findByOrderId(order.getId());
        return toDto(order, details);
    }

    // =================== helper =====================

    private OrderDto toDto(ShopOrder o, List<ShopOrderDetail> details) {
        OrderDto dto = new OrderDto();
        dto.setId(o.getId());
        dto.setCustomerId(o.getCustomerId());
        dto.setPaymentTypeId(o.getPaymentTypeId());
        dto.setUserId(o.getUserId());
        dto.setShipName(o.getShipName());
        dto.setShipAddress(o.getShipAddress());
        dto.setShipCity(o.getShipCity());
        dto.setShipCountry(o.getShipCountry());
        dto.setShipPostalCode(o.getShipPostalCode());
        dto.setShipFee(o.getShipFee());
        dto.setOrderDate(o.getOrderDate());
        dto.setOrderStatus(o.getOrderStatus());

        //  map tổng tiền + giảm giá ra DTO
        dto.setTotalAmount(o.getTotalAmount());
        dto.setDiscountAmount(o.getDiscountAmount());

        List<OrderDetailDto> detailDtos = new ArrayList<>();
        if (details != null) {
            for (ShopOrderDetail d : details) {
                OrderDetailDto dd = new OrderDetailDto();
                dd.setId(d.getId());
                dd.setProductId(d.getProductId());
                dd.setQuantity(d.getQuantity());
                dd.setUnitPrice(d.getUnitPrice());
                dd.setDiscountPercent(d.getDiscountPercent());
                dd.setDiscountAmount(d.getDiscountAmount());
                detailDtos.add(dd);
            }
        }
        dto.setDetails(detailDtos);
        return dto;
    }

    /**
     * Gửi activity log tới auth-service bằng WebClient.
     * Payload tương ứng với ActivityLogDto trên auth-service.
     */
    private void sendActivityLog(Long userId, String username, String action, String resourceType, Long resourceId, String resourceName, String details) {
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("userId", userId);
            payload.put("username", username);
            // include displayName (fallback to username)
            payload.put("displayName", username);
            payload.put("action", action);
            payload.put("resourceType", resourceType);
            payload.put("resourceId", resourceId);
            payload.put("resourceName", resourceName);
            payload.put("details", details);
            String authUrl = System.getenv("AUTH_SERVICE_URL");
            if (authUrl == null || authUrl.isBlank()) {
                authUrl = "http://localhost:8080";
            }
            String uri = authUrl.endsWith("/") ? authUrl + "api/internal/activity-logs" : authUrl + "/api/internal/activity-logs";
            System.out.println("Sending activity log to: " + uri + " payload: " + payload);
            String token = System.getenv("ACTIVITY_LOG_SERVICE_TOKEN");
            org.springframework.web.reactive.function.client.WebClient.RequestBodySpec reqSpec = webClientBuilder.build()
                    .post()
                    .uri(uri);
            if (token != null && !token.isBlank()) {
                reqSpec = reqSpec.header("X-Activity-Log-Token", token);
            }
            reqSpec.bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .doOnSuccess(resp -> System.out.println("Activity log sent, status: " + (resp != null ? resp.getStatusCode() : "null")))
                    .doOnError(err -> System.out.println("Activity log send error: " + err.getMessage()))
                    .block();
        } catch (Exception e) {
            // Bỏ qua lỗi, chỉ log ra console
            System.out.println("sendActivityLog error: " + e.getMessage());
        }
    }

    @Override
    public OrderDto updateStatus(Long id, OrderStatusUpdateRequest req) {
        ShopOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found: " + id));

        String status = req.getStatus();
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }

        order.setOrderStatus(status);
        order.setUpdatedAt(new Date());

        // Nếu muốn tự động set ngày thanh toán / giao hàng:
        if ("PAID".equalsIgnoreCase(status)) {
            order.setPaidDate(new Date());
        }
        if ("SHIPPED".equalsIgnoreCase(status)) {
            order.setShippedDate(new Date());
        }

        order = orderRepo.save(order);

        // Ghi nhật ký cho việc thay đổi trạng thái (duyệt/hủy...)
        try {
            String action = null;
            if ("APPROVED".equalsIgnoreCase(status) || "CONFIRMED".equalsIgnoreCase(status)) {
                action = "APPROVE_ORDER";
            } else if ("CANCELLED".equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status)) {
                action = "CANCEL_ORDER";
            }

            if (action != null) {
                String uname = getCurrentUsername();
                if (uname == null) uname = "system";
                System.out.println("Preparing to send activity log: action=" + action + ", resourceType=ORDER, resourceId=" + order.getId() + ", userId=" + order.getUserId() + ", username=" + uname);
                sendActivityLog(
                        order.getUserId(),
                        uname,
                        action,
                        "ORDER",
                        order.getId(),
                        "ORDER#" + order.getId(),
                        String.format("%s order: %s", action, order.getId())
                );
            }
        } catch (Exception e) {
            System.out.println("Failed to send activity log for status change: " + e.getMessage());
        }

        List<ShopOrderDetail> details = detailRepo.findByOrderId(order.getId());
        return toDto(order, details);
    }

    @Override
    public List<OrderDto> search(Date from, Date to, String status) {
        return orderRepo.findByOrderDateBetweenAndOrderStatus(from, to, status)
                .stream()
                .map(o -> {
                    List<ShopOrderDetail> details = detailRepo.findByOrderId(o.getId());
                    return toDto(o, details);
                })
                .toList();
    }

    @Override
    public OrderSummaryDto summary(Date from, Date to, String status) {
        List<ShopOrder> orders = orderRepo.findByOrderDateBetweenAndOrderStatus(from, to, status);

        OrderSummaryDto dto = new OrderSummaryDto();
        dto.setTotalOrders(orders.size());

        double totalAmount = orders.stream()
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();

        double totalDiscount = orders.stream()
                .mapToDouble(o -> o.getDiscountAmount() != null ? o.getDiscountAmount() : 0.0)
                .sum();

        dto.setTotalAmount(java.math.BigDecimal.valueOf(totalAmount));
        dto.setTotalDiscount(java.math.BigDecimal.valueOf(totalDiscount));

        return dto;
    }

    private String getCurrentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails) {
                return ((org.springframework.security.core.userdetails.UserDetails) auth.getPrincipal()).getUsername();
            }
            if (auth != null && auth.getPrincipal() instanceof String) {
                return (String) auth.getPrincipal();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

}
