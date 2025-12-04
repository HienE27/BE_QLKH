package com.example.inventory_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "shop_import_details")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopImportDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "import_details_id")
    private Long id;

    @Column(name = "imports_id")
    private Long importId;

    @Column(name = "products_id")
    private Long productId;

    @Column(name = "stores_id", nullable = false)
    private Long storeId;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    /**
     * Phần trăm chiết khấu (0-100)
     */
    @Column(name = "discount_percent")
    private BigDecimal discountPercent;
}
