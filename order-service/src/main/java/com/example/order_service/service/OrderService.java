package com.example.order_service.service;

import com.example.order_service.dto.OrderDto;
import com.example.order_service.dto.OrderRequest;
import com.example.order_service.dto.OrderStatusUpdateRequest;
import com.example.order_service.dto.OrderSummaryDto;

import java.util.Date;
import java.util.List;

public interface OrderService {

    OrderDto create(OrderRequest req);

    List<OrderDto> getByCustomer(Long customerId);

    OrderDto getById(Long id);

    // OrderDto updateStatus(Long id, String status);
    OrderDto updateStatus(Long id, OrderStatusUpdateRequest req);

    List<OrderDto> search(Date from, Date to, String status);

    OrderSummaryDto summary(Date from, Date to, String status);

}
