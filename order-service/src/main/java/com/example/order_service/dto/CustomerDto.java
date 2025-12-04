package com.example.order_service.dto;

import lombok.Data;

@Data
public class CustomerDto {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String fullName;
    private String address;
    private String status;
    private String code;
    private String name;
    private String description;
}
