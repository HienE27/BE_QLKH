package com.example.inventory_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {
    // Product statistics
    private Long totalProducts;
    private BigDecimal totalInventoryValue;
    private Integer lowStockCount; // qty > 0 && qty <= 10
    private Integer outOfStockCount; // qty = 0

    // Import statistics
    private Long totalImports; // Chỉ đếm phiếu đã nhập (IMPORTED)
    private BigDecimal totalImportValue;
    private Integer pendingImports;
    private Integer importedCount;

    // Export statistics
    private Long totalExports; // Chỉ đếm phiếu đã xuất (EXPORTED)
    private BigDecimal totalExportValue;
    private Integer pendingExports;
    private Integer exportedCount;

    // Recent activities (chỉ 3-5 bản ghi)
    private List<SupplierImportDto> recentImports;
    private List<SupplierExportDto> recentExports;
}

