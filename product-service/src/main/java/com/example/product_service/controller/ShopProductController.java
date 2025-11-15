package com.example.product_service.controller;

import com.example.product_service.common.ApiResponse;
import com.example.product_service.dto.ProductDto;
import com.example.product_service.dto.ProductRequest;
import com.example.product_service.entity.ShopProductDiscount;
import com.example.product_service.entity.ShopProductImage;
import com.example.product_service.service.ShopProductDiscountService;
import com.example.product_service.service.ShopProductImageService;
import com.example.product_service.service.ShopProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ShopProductController {

    private final ShopProductService productService;
    private final ShopProductImageService imageService;
    private final ShopProductDiscountService discountService;

    public ShopProductController(ShopProductService productService,
                                 ShopProductImageService imageService,
                                 ShopProductDiscountService discountService) {
        this.productService = productService;
        this.imageService = imageService;
        this.discountService = discountService;
    }

    // ====== PRODUCTS ======

    @GetMapping
    public ApiResponse<List<ProductDto>> getAll() {
        return ApiResponse.ok(productService.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductDto> getById(@PathVariable Long id) {
        return ApiResponse.ok(productService.getById(id));
    }

    @PostMapping
    public ApiResponse<ProductDto> create(@RequestBody ProductRequest request) {
        return ApiResponse.ok("Created", productService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductDto> update(@PathVariable Long id,
                                          @RequestBody ProductRequest request) {
        return ApiResponse.ok("Updated", productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ApiResponse.ok("Deleted", null);
    }

    // ====== IMAGES & DISCOUNTS THEO PRODUCT ======

    @GetMapping("/{productId}/images")
    public ApiResponse<List<ShopProductImage>> getImages(@PathVariable Long productId) {
        return ApiResponse.ok(imageService.findByProduct(productId));
    }

    @GetMapping("/{productId}/discounts")
    public ApiResponse<List<ShopProductDiscount>> getDiscounts(@PathVariable Long productId) {
        return ApiResponse.ok(discountService.findByProduct(productId));
    }
}
