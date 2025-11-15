package com.example.order_service.repository;

import com.example.order_service.entity.ShopPaymentType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopPaymentTypeRepository extends JpaRepository<ShopPaymentType, Long> {
}
