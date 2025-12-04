// promotion-service/src/main/java/com/example/promotion_service/dto/IdListRequest.java
package com.example.promotion_service.dto;

import lombok.Data;

import java.util.List;

@Data
public class IdListRequest {
    private List<Long> ids;
}
