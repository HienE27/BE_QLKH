package com.example.promotion_service.repository;

import com.example.promotion_service.entity.ShopVoucher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopVoucherRepository extends JpaRepository<ShopVoucher, Long> {

    // TÌM THEO MÃ VOUCHER => TÊN FIELD TRONG ENTITY LÀ voucherCode
    Optional<ShopVoucher> findByVoucherCode(String voucherCode);

    // nếu có dùng thêm findByType thì giữ, không bắt buộc
    List<ShopVoucher> findByType(String type);
}
