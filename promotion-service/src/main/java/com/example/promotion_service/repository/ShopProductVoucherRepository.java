package com.example.promotion_service.repository;

import com.example.promotion_service.entity.ShopProductVoucher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopProductVoucherRepository extends JpaRepository<ShopProductVoucher, Long> {
    List<ShopProductVoucher> findByProductId(Long productId);
    List<ShopProductVoucher> findByVoucherId(Long voucherId);
}
