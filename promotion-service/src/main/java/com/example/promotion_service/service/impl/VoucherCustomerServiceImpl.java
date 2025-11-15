package com.example.promotion_service.service.impl;

import com.example.promotion_service.dto.VoucherDto;
import com.example.promotion_service.entity.ShopCustomerVoucher;
import com.example.promotion_service.entity.ShopVoucher;
import com.example.promotion_service.repository.ShopCustomerVoucherRepository;
import com.example.promotion_service.repository.ShopVoucherRepository;
import com.example.promotion_service.service.VoucherCustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VoucherCustomerServiceImpl implements VoucherCustomerService {

    private final ShopVoucherRepository voucherRepository;
    private final ShopCustomerVoucherRepository customerVoucherRepository;

    @Override
    public void attachVoucherToCustomers(Long voucherId, List<Long> customerIds) {
        ShopVoucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new RuntimeException("Voucher not found"));

        LocalDateTime now = LocalDateTime.now();

        for (Long customerId : customerIds) {
            ShopCustomerVoucher rel = new ShopCustomerVoucher();
            rel.setVoucher(voucher);          
            rel.setCustomerId(customerId);
            rel.setCreatedAt(now);
            rel.setUpdatedAt(now);
            customerVoucherRepository.save(rel);
        }
    }

    @Override
    public List<VoucherDto> getVouchersOfCustomer(Long customerId) {
        return customerVoucherRepository.findByCustomerId(customerId)
                .stream()
                .map(ShopCustomerVoucher::getVoucher)   // lấy ShopVoucher
                .map(VoucherDto::fromEntity)            // convert sang DTO
                .toList();
    }
}
