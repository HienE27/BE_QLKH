package com.example.order_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Entity
@Table(name = "shop_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @Column(name = "order_date")
    private Date orderDate;

    @Column(name = "shipped_date")
    private Date shippedDate;

    @Column(name = "ship_address")
    private String shipAddress;

    @Column(name = "ship_name")
    private String shipName;

    @Column(name = "ship_city")
    private String shipCity;

    @Column(name = "ship_state")
    private String shipState;

    @Column(name = "ship_country")
    private String shipCountry;

    @Column(name = "ship_postal_code")
    private String shipPostalCode;

    @Column(name = "ship_fee")
    private BigDecimal shipFee;

    @Column(name = "paid_date")
    private Date paidDate;

    @Column(name = "order_status")
    private String orderStatus;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "updated_at")
    private Date updatedAt;

    @Column(name = "user_id")
    private Long userId;            // ad_users.user_id

    @Column(name = "payment_types_id")
    private Long paymentTypeId;     // shop_payment_types.payment_types_id

    @Column(name = "customers_id")
    private Long customerId;        // shop_customers.customers_id

    @Column(name = "total_amount")
    private Double totalAmount;

    @Column(name = "discount_amount")
    private Double discountAmount;

}
