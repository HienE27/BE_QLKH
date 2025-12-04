package com.example.product_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shop_product_discounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopProductDiscount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_discount_id")
    private Long id;

    @Column(name = "discount_name")
    private String discountName;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    @Column(name = "discount_percent")
    private BigDecimal discountPercent;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "products_id")
    private Long productId;
}
