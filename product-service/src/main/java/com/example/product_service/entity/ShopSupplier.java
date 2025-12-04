package com.example.product_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// package com.example.product_service.entity;

@Entity
@Table(name = "shop_suppliers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopSupplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplier_id")
    private Long id;

    @Column(name = "supplier_code")
    private String code;

    @Column(name = "supplier_name")
    private String name;

    // 👇 mới thêm
    @Column(name = "supplier_type")
    private String type;

    @Column(name = "address")
    private String address;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "image")
    private String image;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
