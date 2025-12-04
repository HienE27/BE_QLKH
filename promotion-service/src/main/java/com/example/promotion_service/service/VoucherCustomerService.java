// promotion-service/src/main/java/com/example/promotion_service/service/VoucherCustomerService.java
package com.example.promotion_service.service;

import com.example.promotion_service.dto.VoucherDto;

import java.util.List;

public interface VoucherCustomerService {

    void attachVoucherToCustomers(Long voucherId, List<Long> customerIds);

    List<VoucherDto> getVouchersOfCustomer(Long customerId);
}
