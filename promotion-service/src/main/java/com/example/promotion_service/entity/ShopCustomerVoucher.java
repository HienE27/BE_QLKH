package com.example.promotion_service.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "shop_customer_vouchers")
@Data
public class ShopCustomerVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_vouchers_id")
    private Long id;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // join nội bộ trong promotion-service với bảng shop_vouchers
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vouchers_id", referencedColumnName = "vouchers_id")
    private ShopVoucher voucher;     // <== để dùng setVoucher() và getVoucher()

    // không join sang service customer, chỉ lưu ID
    @Column(name = "customers_id")
    private Long customerId;
}
