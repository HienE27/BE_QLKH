package com.example.order_service.repository;

import com.example.order_service.entity.ShopCustomer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopCustomerRepository extends JpaRepository<ShopCustomer, Long> {
}
