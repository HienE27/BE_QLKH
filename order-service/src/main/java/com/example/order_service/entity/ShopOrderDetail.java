package com.example.order_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Entity
@Table(name = "shop_order_details")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopOrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_details_id")
    private Long id;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "discount_percent")
    private BigDecimal discountPercent;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    @Column(name = "date_allocated")
    private Date dateAllocated;

    @Column(name = "order_details_status")
    private String orderDetailsStatus;

    @Column(name = "date")
    private Date date;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "products_id")
    private Long productId;
}
