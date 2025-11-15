package com.example.order_service.client;

import com.example.order_service.dto.ApiResponse;
import com.example.order_service.dto.VoucherDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class PromotionClient {

    private final WebClient.Builder webClientBuilder;

    public VoucherDto getVoucherByCode(String code) {
        ApiResponse<VoucherDto> response = webClientBuilder.build()
                .get()
                .uri("http://promotion-service/api/vouchers/code/{code}", code)
                .retrieve()
                .bodyToMono(ApiResponseVoucherResponse.class)
                .block()
                .toApiResponse();

        if (response == null || !response.isSuccess() || response.getData() == null) {
            return null;
        }
        return response.getData();
    }

    /**
     * Trick nhỏ để map JSON có generic ApiResponse<VoucherDto>
     */
    private static class ApiResponseVoucherResponse extends ApiResponse<VoucherDto> {
        public ApiResponse<VoucherDto> toApiResponse() {
            ApiResponse<VoucherDto> res = new ApiResponse<>();
            res.setSuccess(this.isSuccess());
            res.setMessage(this.getMessage());
            res.setData(this.getData());
            return res;
        }
    }
}
