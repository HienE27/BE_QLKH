package com.example.order_service.controller;

import com.example.order_service.common.ApiResponse;
import com.example.order_service.dto.OrderDto;
import com.example.order_service.dto.OrderRequest;
import com.example.order_service.dto.OrderStatusUpdateRequest;
import com.example.order_service.dto.OrderSummaryDto;
import com.example.order_service.service.OrderService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<OrderDto> create(@RequestBody OrderRequest req) {
        return ApiResponse.ok("Created", service.create(req));
    }

    @GetMapping("/by-customer/{customerId}")
    public ApiResponse<List<OrderDto>> getByCustomer(@PathVariable Long customerId) {
        return ApiResponse.ok(service.getByCustomer(customerId));
    }

    // Xem chi tiết một đơn
    @GetMapping("/{id}")
    public ApiResponse<OrderDto> getById(@PathVariable Long id) {
        return ApiResponse.ok(service.getById(id));
    }

    // API cập nhật trạng thái
    @PatchMapping("/{id}/status")
    public ApiResponse<OrderDto> updateStatus(
            @PathVariable Long id,
            @RequestBody OrderStatusUpdateRequest req) {

        return ApiResponse.ok(
                "Updated status",
                service.updateStatus(id, req) // truyền cả DTO
        );
    }

    // OrderController
    @GetMapping("/search")
    public ApiResponse<List<OrderDto>> search(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam("status") String status) {

        Date fromDate = java.sql.Date.valueOf(from);
        Date toDate = java.sql.Date.valueOf(to);

        return ApiResponse.ok(service.search(fromDate, toDate, status));
    }

    @GetMapping("/summary")
public ApiResponse<OrderSummaryDto> summary(
        @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam("to")   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam("status") String status) {

    Date fromDate = java.sql.Date.valueOf(from);
    Date toDate   = java.sql.Date.valueOf(to);

    return ApiResponse.ok(
            "Order summary",
            service.summary(fromDate, toDate, status)
    );
}


}
