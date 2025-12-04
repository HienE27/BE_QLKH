package com.example.order_service.service.impl;

import com.example.order_service.dto.CustomerDto;
import com.example.order_service.dto.CustomerRequest;
import com.example.order_service.entity.ShopCustomer;
import com.example.order_service.exception.NotFoundException;
import com.example.order_service.repository.ShopCustomerRepository;
import com.example.order_service.service.CustomerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
    public Page<CustomerDto> search(String code, String name, String phone, Pageable pageable) {
        Page<ShopCustomer> page = repo.search(code, name, phone, pageable);
        List<CustomerDto> content = page.getContent().stream()
                .map(this::toDto)
                .toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
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
        // Tự động tạo mã nếu không có trong request
        if (req.getCode() != null && !req.getCode().isBlank()) {
            c.setCode(req.getCode());
        } else {
            c.setCode(generateCustomerCode());
        }
        c.setName(req.getName());
        c.setDescription(req.getDescription());
        c.setStatus("ACTIVE");
        c.setCreatedAt(new Date());
        c.setUpdatedAt(new Date());
        return toDto(repo.save(c));
    }

    /**
     * Tự động tạo mã khách hàng: KH + 5 số (ví dụ: KH00001)
     */
    private String generateCustomerCode() {
        String prefix = "KH";
        List<ShopCustomer> existing = repo.findByCodeStartingWith(prefix);
        long maxNumber = 0;

        for (ShopCustomer c : existing) {
            if (c.getCode() != null && c.getCode().length() >= 3) {
                try {
                    String numberPart = c.getCode().substring(2); // Lấy phần số sau 2 ký tự đầu
                    long num = Long.parseLong(numberPart);
                    if (num > maxNumber) {
                        maxNumber = num;
                    }
                } catch (NumberFormatException e) {
                    // Bỏ qua nếu không parse được số
                }
            }
        }

        long nextNumber = maxNumber + 1;
        return prefix + String.format("%05d", nextNumber);
    }

    @Override
    public CustomerDto update(Long id, CustomerRequest req) {
        ShopCustomer c = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Customer not found: " + id));

        if (req.getUsername() != null)
            c.setUsername(req.getUsername());
        if (req.getPassword() != null)
            c.setPassword(req.getPassword());
        if (req.getEmail() != null)
            c.setEmail(req.getEmail());
        if (req.getPhone() != null)
            c.setPhone(req.getPhone());
        if (req.getFirstName() != null)
            c.setFirstName(req.getFirstName());
        if (req.getLastName() != null)
            c.setLastName(req.getLastName());
        if (req.getAddress() != null)
            c.setAddress(req.getAddress());
        if (req.getCountry() != null)
            c.setCountry(req.getCountry());
        if (req.getCode() != null)
            c.setCode(req.getCode());
        if (req.getName() != null)
            c.setName(req.getName());
        if (req.getDescription() != null)
            c.setDescription(req.getDescription());
        c.setUpdatedAt(new Date());

        return toDto(repo.save(c));
    }

    @Override
    public void delete(Long id) {
        ShopCustomer c = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Customer not found: " + id));
        repo.delete(c);
    }

    private CustomerDto toDto(ShopCustomer c) {
        CustomerDto dto = new CustomerDto();
        dto.setId(c.getId());
        dto.setUsername(c.getUsername());
        dto.setEmail(c.getEmail());
        dto.setPhone(c.getPhone());
        // Use name field if available, otherwise fallback to firstName + lastName
        if (c.getName() != null && !c.getName().isEmpty()) {
            dto.setFullName(c.getName());
        } else {
            String fullName = (c.getLastName() != null ? c.getLastName() : "") +
                    " " + (c.getFirstName() != null ? c.getFirstName() : "");
            dto.setFullName(fullName.trim().isEmpty() ? null : fullName.trim());
        }
        dto.setName(c.getName());
        dto.setAddress(c.getAddress());
        dto.setStatus(c.getStatus());
        dto.setCode(c.getCode());
        dto.setDescription(c.getDescription());
        return dto;
    }
}
