package com.example.order_service.repository;

import com.example.order_service.entity.ShopOrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopOrderDetailRepository extends JpaRepository<ShopOrderDetail, Long> {
    List<ShopOrderDetail> findByOrderId(Long orderId);
}
