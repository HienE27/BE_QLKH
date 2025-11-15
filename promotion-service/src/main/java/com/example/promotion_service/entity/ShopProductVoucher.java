package com.example.promotion_service.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "shop_product_vouchers")
@Data
public class ShopProductVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_vouchers_id")
    private Long id;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ID voucher (không map ManyToOne sang voucher-service)
    @Column(name = "vouchers_id")
    private Long voucherId;

    // ID sản phẩm (không map ManyToOne sang product-service)
    @Column(name = "products_id")
    private Long productId;
}
