package com.example.inventory_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "shop_inventory_check_details")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCheckDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_check_detail_id")
    private Long id;

    @Column(name = "inventory_check_id", nullable = false)
    private Long inventoryCheckId;

    @Column(name = "products_id", nullable = false)
    private Long productId;

    @Column(name = "system_quantity", nullable = false)
    private Integer systemQuantity; // Số lượng trên hệ thống

    @Column(name = "actual_quantity", nullable = false)
    private Integer actualQuantity; // Số lượng thực tế

    @Column(name = "difference_quantity", nullable = false)
    private Integer differenceQuantity; // Chênh lệch

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "total_value")
    private BigDecimal totalValue;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
