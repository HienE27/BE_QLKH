package com.example.product_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "shop_products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "products_id") // đúng với SQL
    private Long id;

    @Column(name = "product_code")
    private String code;

    @Column(name = "product_name")
    private String name;

    @Column(name = "short_description", columnDefinition = "TEXT") // TEXT để lưu mô tả dài từ AI
    private String shortDescription;

    @Column(name = "image")
    private String image;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "status")
    private String status;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "supplier_id")
    private Long supplierId; // NCC chính (tương thích ngược)

    @Column(name = "supplier_ids", columnDefinition = "TEXT")
    private String supplierIds; // Danh sách NCC dưới dạng JSON: [1,2,3]

    @Column(name = "unit_id")
    private Long unitId;

    @Column(name = "created_at")
    private java.util.Date createdAt;

    @Column(name = "updated_at")
    private java.util.Date updatedAt;
}
