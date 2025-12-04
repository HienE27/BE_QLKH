package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryForecastResponse {
    private String recommendation;
    private List<SimilarSnapshot> similarSnapshots; // Các snapshot tồn kho tương tự từ Milvus
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarSnapshot {
        private String snapshotTime; // Thời gian snapshot
        private Double similarityScore; // Độ tương tự (score từ Milvus, càng thấp càng tương tự)
        private String summary; // Tóm tắt snapshot (từ metadata)
    }
}


