package com.example.promotion_service.service.impl;

import com.example.promotion_service.dto.IdListRequest;
import com.example.promotion_service.dto.VoucherDto;
import com.example.promotion_service.dto.VoucherRequest;
import com.example.promotion_service.entity.ShopProductVoucher;
import com.example.promotion_service.entity.ShopVoucher;
import com.example.promotion_service.exception.NotFoundException;
import com.example.promotion_service.repository.ShopProductVoucherRepository;
import com.example.promotion_service.repository.ShopVoucherRepository;
import com.example.promotion_service.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {

    private final ShopVoucherRepository voucherRepository;
    private final ShopProductVoucherRepository productVoucherRepository;

    @Override
    public List<VoucherDto> getAll() {
        return voucherRepository.findAll()
                .stream()
                .map(VoucherDto::fromEntity)
                .toList();
    }

    @Override
    public VoucherDto create(VoucherRequest request) {
        ShopVoucher voucher = new ShopVoucher();
        // chú ý: field trong entity là voucherCode
        voucher.setVoucherCode(request.getCode());
        voucher.setDescription(request.getDescription());

        if (request.getDiscountAmount() != null) {
            // convert Double -> BigDecimal
            voucher.setDiscountAmount(BigDecimal.valueOf(request.getDiscountAmount()));
        }

        voucher.setStartDate(request.getStartDate());
        voucher.setEndDate(request.getEndDate());
        voucher.setCreatedAt(LocalDateTime.now());
        voucher.setUpdatedAt(LocalDateTime.now());

        ShopVoucher saved = voucherRepository.save(voucher);
        return VoucherDto.fromEntity(saved);
    }

    @Override
    public void attachToProducts(Long voucherId, IdListRequest request) {
        if (!voucherRepository.existsById(voucherId)) {
            throw new NotFoundException("Voucher not found with id: " + voucherId);
        }

        LocalDateTime now = LocalDateTime.now();
        for (Long productId : request.getIds()) {
            ShopProductVoucher pv = new ShopProductVoucher();
            pv.setVoucherId(voucherId);
            pv.setProductId(productId);
            pv.setCreatedAt(now);
            pv.setUpdatedAt(now);
            productVoucherRepository.save(pv);
        }
    }

    @Override
    public Optional<VoucherDto> getByCode(String code) {
        return voucherRepository.findByVoucherCode(code)
                .map(VoucherDto::fromEntity);
    }
}
