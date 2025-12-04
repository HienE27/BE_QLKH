package com.example.promotion_service.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shop_vouchers")
@Data
public class ShopVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vouchers_id")
    private Long id;

    @Column(name = "voucher_code")
    private String voucherCode;  

    @Column(name = "description")
    private String description;

    @Column(name = "`user`")      
    private String user;

    @Column(name = "max_user")
    private Integer maxUser;

    @Column(name = "type")
    private String type;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
