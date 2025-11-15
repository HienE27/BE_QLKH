package com.example.order_service.repository;

import com.example.order_service.entity.ShopOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;

public interface ShopOrderRepository extends JpaRepository<ShopOrder, Long> {
    List<ShopOrder> findByCustomerId(Long customerId);

    // tìm theo khoảng ngày & trạng thái
    List<ShopOrder> findByOrderDateBetweenAndOrderStatus(Date from, Date to, String status);
}
