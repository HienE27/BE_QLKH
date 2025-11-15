package com.example.promotion_service.controller;

import com.example.promotion_service.common.ApiResponse;
import com.example.promotion_service.dto.IdListRequest;
import com.example.promotion_service.dto.VoucherDto;
import com.example.promotion_service.dto.VoucherRequest;
import com.example.promotion_service.service.VoucherCustomerService;
import com.example.promotion_service.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;
    private final VoucherCustomerService voucherCustomerService;

    // Lấy tất cả voucher
    @GetMapping
    public ApiResponse<List<VoucherDto>> getAll() {
        return ApiResponse.ok(voucherService.getAll());
    }

    // GET /api/vouchers/code/{code}
    // Dùng cho order-service check voucher theo mã
    @GetMapping("/code/{code}")
    public ApiResponse<VoucherDto> getByCode(@PathVariable String code) {
        return voucherService.getByCode(code)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("Voucher not found with code: " + code));
    }

    // Tạo mới voucher
    @PostMapping
    public ApiResponse<VoucherDto> create(@RequestBody VoucherRequest request) {
        return ApiResponse.ok("Created", voucherService.create(request));
    }

    // Gắn voucher cho nhiều sản phẩm
    @PostMapping("/{voucherId}/products")
    public ApiResponse<Void> attachToProducts(
            @PathVariable Long voucherId,
            @RequestBody IdListRequest request
    ) {
        voucherService.attachToProducts(voucherId, request);
        return ApiResponse.ok("Attached to products", null);
    }

    // Gắn voucher cho nhiều customer
    @PostMapping("/{voucherId}/customers")
    public ApiResponse<Void> attachToCustomers(
            @PathVariable Long voucherId,
            @RequestBody IdListRequest request
    ) {
        voucherCustomerService.attachVoucherToCustomers(voucherId, request.getIds());
        return ApiResponse.ok("Attached to customers", null);
    }

    // Lấy danh sách voucher của 1 customer
    @GetMapping("/by-customer/{customerId}")
    public ApiResponse<List<VoucherDto>> getByCustomer(
            @PathVariable Long customerId
    ) {
        return ApiResponse.ok(
                "Vouchers of customer",
                voucherCustomerService.getVouchersOfCustomer(customerId)
        );
    }
}
