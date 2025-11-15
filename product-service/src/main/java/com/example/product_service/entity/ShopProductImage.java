package com.example.product_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shop_product_images")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_images_id")
    private Long id;

    @Column(name = "image")
    private String image;

    @Column(name = "products_id")
    private Long productId;
}
