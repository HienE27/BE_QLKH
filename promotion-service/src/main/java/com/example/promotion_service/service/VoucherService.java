package com.example.promotion_service.service;

import com.example.promotion_service.dto.IdListRequest;
import com.example.promotion_service.dto.VoucherDto;
import com.example.promotion_service.dto.VoucherRequest;

import java.util.List;
import java.util.Optional;

public interface VoucherService {

    List<VoucherDto> getAll();

    VoucherDto create(VoucherRequest request);

    // Gắn voucher cho nhiều sản phẩm
    void attachToProducts(Long voucherId, IdListRequest request);

    Optional<VoucherDto> getByCode(String code);
}
