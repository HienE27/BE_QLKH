package com.example.promotion_service.dto;

import com.example.promotion_service.entity.ShopVoucher;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class VoucherDto {

    private Long id;
    private String code;
    private String description;
    private BigDecimal discountAmount;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    public static VoucherDto fromEntity(ShopVoucher entity) {
        VoucherDto dto = new VoucherDto();
        dto.setId(entity.getId());
        // field trong entity là voucherCode
        dto.setCode(entity.getVoucherCode());
        dto.setDescription(entity.getDescription());
        dto.setDiscountAmount(entity.getDiscountAmount());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        return dto;
    }
}
