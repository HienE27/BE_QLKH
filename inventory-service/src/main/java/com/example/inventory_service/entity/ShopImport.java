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

    @Column(name = "import_code")
    private String code;

    @Column(name = "import_type")
    private String importType; // "SUPPLIER"

    @Column(name = "status")
    private String status;

    @Column(name = "imports_date")
    private Date importsDate;

    @Column(name = "stores_id")
    private Long storeId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "note")
    private String note;

    @Column(name = "description")
    private String description;

    @Column(name = "attachment_image")
    private String attachmentImage; // /uploads/...

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "updated_at")
    private Date updatedAt;
}
