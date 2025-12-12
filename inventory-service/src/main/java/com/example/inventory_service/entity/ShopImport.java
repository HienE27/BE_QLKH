package com.example.inventory_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "shop_imports", indexes = {
        @Index(name = "idx_import_type_status_date", columnList = "import_type, status, imports_date"),
        @Index(name = "idx_import_code", columnList = "import_code"),
        @Index(name = "idx_imports_date", columnList = "imports_date"),
        @Index(name = "idx_imports_store_id", columnList = "stores_id"),
        @Index(name = "idx_imports_supplier_id", columnList = "supplier_id")
})
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

    @Enumerated(EnumType.STRING)
    @Column(name = "import_type")
    private ImportType importType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ImportStatus status;

    @Column(name = "imports_date", columnDefinition = "DATETIME(6)")
    private LocalDateTime importsDate;

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

    @Column(name = "created_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    // Audit fields
    @Column(name = "created_by")
    private Long createdBy; // userId của người tạo

    @Column(name = "approved_by")
    private Long approvedBy; // userId của người duyệt

    @Column(name = "approved_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_by")
    private Long rejectedBy; // userId của người từ chối

    @Column(name = "rejected_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime rejectedAt;

    @Column(name = "imported_by")
    private Long importedBy; // userId của người nhập kho

    @Column(name = "imported_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime importedAt;
}
