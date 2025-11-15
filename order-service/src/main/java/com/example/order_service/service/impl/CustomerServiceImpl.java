package com.example.order_service.service.impl;

import com.example.order_service.dto.CustomerDto;
import com.example.order_service.dto.CustomerRequest;
import com.example.order_service.entity.ShopCustomer;
import com.example.order_service.exception.NotFoundException;
import com.example.order_service.repository.ShopCustomerRepository;
import com.example.order_service.service.CustomerService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final ShopCustomerRepository repo;

    public CustomerServiceImpl(ShopCustomerRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<CustomerDto> getAll() {
        return repo.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public CustomerDto getById(Long id) {
        ShopCustomer c = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Customer not found: " + id));
        return toDto(c);
    }

    @Override
    public CustomerDto create(CustomerRequest req) {
        ShopCustomer c = new ShopCustomer();
        c.setUsername(req.getUsername());
        c.setPassword(req.getPassword());
        c.setEmail(req.getEmail());
        c.setPhone(req.getPhone());
        c.setFirstName(req.getFirstName());
        c.setLastName(req.getLastName());
        c.setAddress(req.getAddress());
        c.setCountry(req.getCountry());
        c.setStatus("ACTIVE");
        c.setCreatedAt(new Date());
        c.setUpdatedAt(new Date());
        return toDto(repo.save(c));
    }

    private CustomerDto toDto(ShopCustomer c) {
        CustomerDto dto = new CustomerDto();
        dto.setId(c.getId());
        dto.setUsername(c.getUsername());
        dto.setEmail(c.getEmail());
        dto.setPhone(c.getPhone());
        dto.setFullName(c.getLastName() + " " + c.getFirstName());
        dto.setAddress(c.getAddress());
        dto.setStatus(c.getStatus());
        return dto;
    }
}
