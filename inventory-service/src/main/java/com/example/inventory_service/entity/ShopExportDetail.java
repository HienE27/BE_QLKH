package com.example.inventory_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "shop_export_details")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopExportDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "export_details_id")
    private Long id;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "import_details_id")
    private Long importDetailId;  // FK -> shop_import_details.import_details_id

    @Column(name = "exports_id")
    private Long exportId;        // FK -> shop_exports.exports_id

    @Column(name = "products_id")
    private Long productId;       // FK -> shop_products.products_id
}
