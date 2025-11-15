package com.example.order_service.controller;

import com.example.order_service.common.ApiResponse;
import com.example.order_service.dto.CustomerDto;
import com.example.order_service.dto.CustomerRequest;
import com.example.order_service.service.CustomerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<CustomerDto>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @PostMapping
    public ApiResponse<CustomerDto> create(@RequestBody CustomerRequest req) {
        return ApiResponse.ok("Created", service.create(req));
    }
}
