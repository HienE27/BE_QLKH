package com.example.order_service.service;

import com.example.order_service.dto.CustomerDto;
import com.example.order_service.dto.CustomerRequest;

import java.util.List;

public interface CustomerService {
    List<CustomerDto> getAll();
    CustomerDto getById(Long id);
    CustomerDto create(CustomerRequest req);
}
