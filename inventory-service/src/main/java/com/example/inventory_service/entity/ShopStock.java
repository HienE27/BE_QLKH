package com.example.inventory_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shop_stocks", uniqueConstraints = {
    @UniqueConstraint(name = "uk_stock_product_store", columnNames = {"products_id", "stores_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long id;

    @Column(name = "products_id", nullable = false)
    private Long productId;

    @Column(name = "stores_id", nullable = false)
    private Long storeId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 0;

    @Column(name = "min_stock")
    private Integer minStock = 0;

    @Column(name = "max_stock")
    private Integer maxStock = 999999;
}

