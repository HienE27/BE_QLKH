package com.example.inventory_service.service.impl;

import com.example.inventory_service.client.ProductServiceClient;
import com.example.inventory_service.dto.DashboardStatsDto;
import com.example.inventory_service.dto.SupplierExportDto;
import com.example.inventory_service.dto.SupplierImportDto;
import com.example.inventory_service.entity.ShopExport;
import com.example.inventory_service.entity.ShopExportDetail;
import com.example.inventory_service.entity.ShopImport;
import com.example.inventory_service.entity.ShopImportDetail;
import com.example.inventory_service.entity.ShopStock;
import com.example.inventory_service.repository.ShopExportDetailRepository;
import com.example.inventory_service.repository.ShopExportRepository;
import com.example.inventory_service.repository.ShopImportDetailRepository;
import com.example.inventory_service.repository.ShopImportRepository;
import com.example.inventory_service.repository.ShopStockRepository;
import com.example.inventory_service.service.DashboardService;
import com.example.inventory_service.service.ExportService;
import com.example.inventory_service.service.ImportService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final ProductServiceClient productClient;
    private final ShopStockRepository stockRepo;
    private final ShopImportRepository importRepo;
    private final ShopExportRepository exportRepo;
    private final ShopImportDetailRepository importDetailRepo;
    private final ShopExportDetailRepository exportDetailRepo;
    private final ImportService importService;
    private final ExportService exportService;

    public DashboardServiceImpl(
            ProductServiceClient productClient,
            ShopStockRepository stockRepo,
            ShopImportRepository importRepo,
            ShopExportRepository exportRepo,
            ShopImportDetailRepository importDetailRepo,
            ShopExportDetailRepository exportDetailRepo,
            ImportService importService,
            ExportService exportService) {
        this.productClient = productClient;
        this.stockRepo = stockRepo;
        this.importRepo = importRepo;
        this.exportRepo = exportRepo;
        this.importDetailRepo = importDetailRepo;
        this.exportDetailRepo = exportDetailRepo;
        this.importService = importService;
        this.exportService = exportService;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats() {
        DashboardStatsDto stats = new DashboardStatsDto();

        try {
            // 1. Lấy tất cả sản phẩm từ product-service
            List<ProductServiceClient.ProductDto> products = productClient.getAllProducts();
            if (products == null) {
                products = new java.util.ArrayList<>();
            }
            stats.setTotalProducts((long) products.size());

            // 2. Lấy tồn kho từ shop_stocks và tính toán
        List<ShopStock> stocks = stockRepo.findAll();
        Map<Long, Integer> stockMap = stocks.stream()
                .collect(Collectors.groupingBy(
                        ShopStock::getProductId,
                        Collectors.summingInt(ShopStock::getQuantity)));

        // 3. Tính giá trị tồn kho, low stock, out of stock
        BigDecimal totalInventoryValue = BigDecimal.ZERO;
        int lowStockCount = 0;
        int outOfStockCount = 0;

        Map<Long, BigDecimal> productPriceMap = products.stream()
                .filter(p -> p.getUnitPrice() != null)
                .collect(Collectors.toMap(
                        ProductServiceClient.ProductDto::getId,
                        ProductServiceClient.ProductDto::getUnitPrice,
                        (existing, replacement) -> existing));

        for (ProductServiceClient.ProductDto product : products) {
            Long productId = product.getId();
            Integer quantity = stockMap.getOrDefault(productId, 0);
            BigDecimal unitPrice = productPriceMap.getOrDefault(productId, BigDecimal.ZERO);

            // Tính giá trị tồn kho
            totalInventoryValue = totalInventoryValue.add(
                    unitPrice.multiply(BigDecimal.valueOf(quantity)));

            // Đếm low stock (qty > 0 && qty <= 10)
            if (quantity > 0 && quantity <= 10) {
                lowStockCount++;
            }

            // Đếm out of stock (qty = 0)
            if (quantity == 0) {
                outOfStockCount++;
            }
        }

        stats.setTotalInventoryValue(totalInventoryValue);
        stats.setLowStockCount(lowStockCount);
        stats.setOutOfStockCount(outOfStockCount);

        // 4. Tính thống kê imports
        List<ShopImport> allImports = importRepo.findAll();
        List<ShopImport> importedItems = allImports.stream()
                .filter(i -> "IMPORTED".equals(i.getStatus()))
                .collect(Collectors.toList());
        stats.setTotalImports((long) importedItems.size());
        stats.setImportedCount(importedItems.size());

        // Tính tổng giá trị imports (tính trực tiếp từ details để tối ưu)
        BigDecimal totalImportValue = BigDecimal.ZERO;
        for (ShopImport imp : importedItems) {
            List<ShopImportDetail> details = importDetailRepo.findByImportId(imp.getId());
            BigDecimal importTotal = BigDecimal.ZERO;
            for (ShopImportDetail d : details) {
                if (d.getUnitPrice() != null && d.getQuantity() != null) {
                    BigDecimal line = d.getUnitPrice().multiply(BigDecimal.valueOf(d.getQuantity()));
                    if (d.getDiscountPercent() != null && d.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal discountMultiplier = BigDecimal.ONE
                                .subtract(d.getDiscountPercent().divide(BigDecimal.valueOf(100), 4,
                                        java.math.RoundingMode.HALF_UP));
                        line = line.multiply(discountMultiplier);
                    }
                    importTotal = importTotal.add(line);
                }
            }
            totalImportValue = totalImportValue.add(importTotal);
        }
        stats.setTotalImportValue(totalImportValue);

        int pendingImports = (int) allImports.stream()
                .filter(i -> "PENDING".equals(i.getStatus()))
                .count();
        stats.setPendingImports(pendingImports);

        // 5. Tính thống kê exports
        List<ShopExport> allExports = exportRepo.findAll();
        List<ShopExport> exportedItems = allExports.stream()
                .filter(e -> "EXPORTED".equals(e.getStatus()))
                .collect(Collectors.toList());
        stats.setTotalExports((long) exportedItems.size());
        stats.setExportedCount(exportedItems.size());

        // Tính tổng giá trị exports (tính trực tiếp từ details để tối ưu)
        BigDecimal totalExportValue = BigDecimal.ZERO;
        for (ShopExport exp : exportedItems) {
            List<ShopExportDetail> details = exportDetailRepo.findByExportId(exp.getId());
            BigDecimal exportTotal = BigDecimal.ZERO;
            for (ShopExportDetail d : details) {
                if (d.getUnitPrice() != null && d.getQuantity() != null) {
                    BigDecimal line = d.getUnitPrice().multiply(BigDecimal.valueOf(d.getQuantity()));
                    if (d.getDiscountPercent() != null && d.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal discountMultiplier = BigDecimal.ONE
                                .subtract(d.getDiscountPercent().divide(BigDecimal.valueOf(100), 4,
                                        java.math.RoundingMode.HALF_UP));
                        line = line.multiply(discountMultiplier);
                    }
                    exportTotal = exportTotal.add(line);
                }
            }
            totalExportValue = totalExportValue.add(exportTotal);
        }
        stats.setTotalExportValue(totalExportValue);

        int pendingExports = (int) allExports.stream()
                .filter(e -> "PENDING".equals(e.getStatus()))
                .count();
        stats.setPendingExports(pendingExports);

        // 6. Lấy recent imports/exports (chỉ 3-5 bản ghi)
        Pageable pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "importsDate"));
        List<ShopImport> recentImports = importRepo.findAll(pageable).getContent();
        stats.setRecentImports(recentImports.stream()
                .map(imp -> importService.getById(imp.getId()))
                .collect(Collectors.toList()));

        Pageable exportPageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "exportsDate"));
        List<ShopExport> recentExports = exportRepo.findAll(exportPageable).getContent();
            stats.setRecentExports(recentExports.stream()
                    .map(exp -> exportService.getById(exp.getId()))
                    .collect(Collectors.toList()));

            return stats;
        } catch (Exception e) {
            System.err.println("❌ Error in getDashboardStats: " + e.getMessage());
            e.printStackTrace();
            // Trả về stats với giá trị mặc định thay vì throw exception
            // Để dashboard vẫn hiển thị được (có thể là dữ liệu rỗng)
            return stats;
        }
    }
}

