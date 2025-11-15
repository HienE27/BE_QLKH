// promotion-service/src/main/java/com/example/promotion_service/repository/ShopCustomerVoucherRepository.java
package com.example.promotion_service.repository;

import com.example.promotion_service.entity.ShopCustomerVoucher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopCustomerVoucherRepository extends JpaRepository<ShopCustomerVoucher, Long> {

    List<ShopCustomerVoucher> findByCustomerId(Long customerId);
    

    boolean existsByCustomerIdAndVoucherId(Long customerId, Long voucherId);
}
