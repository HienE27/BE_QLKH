package com.example.order_service.dto;

import lombok.Data;

@Data
public class CustomerRequest {
    private String username;
    private String password;
    private String email;
    private String phone;
    private String lastName;
    private String firstName;
    private String address;
    private String country;
    private String code;
    private String name;
    private String description;
}
