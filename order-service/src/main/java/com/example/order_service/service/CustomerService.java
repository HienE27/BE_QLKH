package com.example.order_service.service;

import com.example.order_service.dto.CustomerDto;
import com.example.order_service.dto.CustomerRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CustomerService {
    List<CustomerDto> getAll();
    Page<CustomerDto> search(String code, String name, String phone, Pageable pageable);
    CustomerDto getById(Long id);
    CustomerDto create(CustomerRequest req);
    CustomerDto update(Long id, CustomerRequest req);
    void delete(Long id);
}
