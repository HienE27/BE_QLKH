package com.example.inventory_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Entity
@Table(name = "shop_exports")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopExport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exports_id")
    private Long id;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "updated_at")
    private Date updatedAt;

    @Column(name = "description")
    private String description;

    @Column(name = "exports_date")
    private Date exportsDate;

    @Column(name = "stores_id")
    private Long storeId;   // FK -> shop_stores

    @Column(name = "user_id")
    private Long userId;    // FK -> ad_users

    @Column(name = "order_id")
    private Long orderId;   // FK -> shop_orders
}
