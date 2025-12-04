package com.example.order_service.controller;

import com.example.order_service.common.ApiResponse;
import com.example.order_service.dto.CustomerDto;
import com.example.order_service.dto.CustomerRequest;
import com.example.order_service.service.CustomerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Page<CustomerDto>> getAll(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            Pageable pageable) {
        Page<CustomerDto> page = service.search(code, name, phone, pageable);
        return ApiResponse.ok(page);
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerDto> getById(@PathVariable Long id) {
        return ApiResponse.ok(service.getById(id));
    }

    @PostMapping
    public ApiResponse<CustomerDto> create(@RequestBody CustomerRequest req) {
        return ApiResponse.ok("Created", service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<CustomerDto> update(@PathVariable Long id, @RequestBody CustomerRequest req) {
        return ApiResponse.ok("Updated", service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}
