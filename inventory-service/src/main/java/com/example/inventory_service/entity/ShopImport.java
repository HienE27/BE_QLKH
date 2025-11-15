package com.example.inventory_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Entity
@Table(name = "shop_imports")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "imports_id")
    private Long id;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "updated_at")
    private Date updatedAt;

    @Column(name = "user_id")
    private Long userId;      // tham chiếu sang ad_users (ID thôi)

    @Column(name = "stores_id")
    private Long storeId;     // tham chiếu sang shop_stores
}
