package com.example.settings_cms_service.entity;

//import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shop_posts")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ShopPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_slug", length = 191)
    private String slug;

    @Column(name = "post_title", length = 255)
    private String title;

    @Column(name = "post_content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "post_excerpt", length = 255)
    private String excerpt;

    @Column(name = "post_status", length = 30)
    private String status;

    @Column(name = "post_image", length = 255)
    private String image;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cate")
    
    private ShopPostCategory category;

    @Column(name = "user_id")
    private Long userId;   // id user bên AUTH-SERVICE
}
