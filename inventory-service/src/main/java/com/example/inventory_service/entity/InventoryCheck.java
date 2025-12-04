package com.example.inventory_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Entity
@Table(name = "shop_inventory_checks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_check_id")
    private Long id;

    @Column(name = "check_code", unique = true, nullable = false)
    private String checkCode;

    @Column(name = "stores_id", nullable = false)
    private Long storeId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false)
    private String status; // PENDING, APPROVED, REJECTED

    @Column(name = "check_date", nullable = false)
    private Date checkDate;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Date approvedAt;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "attachment_image", columnDefinition = "TEXT")
    private String attachmentImage;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "updated_at")
    private Date updatedAt;
}
