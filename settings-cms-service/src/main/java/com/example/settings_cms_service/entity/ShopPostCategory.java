package com.example.settings_cms_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "shop_post_categories")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ShopPostCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cate")
    private Long id;

    @Column(name = "post_category_code", length = 100)
    private String code;

    @Column(name = "post_category_name", length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String image;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
