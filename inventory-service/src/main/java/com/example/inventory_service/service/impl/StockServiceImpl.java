package com.example.inventory_service.service.impl;

import com.example.inventory_service.dto.StockDto;
import com.example.inventory_service.entity.ShopExportDetail;
import com.example.inventory_service.entity.ShopImportDetail;
import com.example.inventory_service.exception.NotFoundException;
import com.example.inventory_service.repository.ShopExportDetailRepository;
import com.example.inventory_service.repository.ShopImportDetailRepository;
import com.example.inventory_service.service.StockService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StockServiceImpl implements StockService {

    private final ShopImportDetailRepository importDetailRepo;
    private final ShopExportDetailRepository exportDetailRepo;

    public StockServiceImpl(ShopImportDetailRepository importDetailRepo,
                            ShopExportDetailRepository exportDetailRepo) {
        this.importDetailRepo = importDetailRepo;
        this.exportDetailRepo = exportDetailRepo;
    }

    @Override
    public List<StockDto> getAllStock() {
        // lấy tất cả import_details & export_details
        List<ShopImportDetail> imports = importDetailRepo.findAll();
        List<ShopExportDetail> exports = exportDetailRepo.findAll();

        // map productId -> [importQty, exportQty]
        Map<Long, StockDto> map = new HashMap<>();

        // cộng nhập
        for (ShopImportDetail imp : imports) {
            Long productId = imp.getProductId();
            StockDto dto = map.computeIfAbsent(productId, id -> {
                StockDto s = new StockDto();
                s.setProductId(id);
                s.setImportedQty(0);
                s.setExportedQty(0);
                s.setCurrentQty(0);
                return s;
            });
            int qty = imp.getQuantity() != null ? imp.getQuantity() : 0;
            dto.setImportedQty(dto.getImportedQty() + qty);
        }

        // cộng xuất
        for (ShopExportDetail ex : exports) {
            Long productId = ex.getProductId();
            StockDto dto = map.computeIfAbsent(productId, id -> {
                StockDto s = new StockDto();
                s.setProductId(id);
                s.setImportedQty(0);
                s.setExportedQty(0);
                s.setCurrentQty(0);
                return s;
            });
            int qty = ex.getQuantity() != null ? ex.getQuantity() : 0;
            dto.setExportedQty(dto.getExportedQty() + qty);
        }

        // tính tồn kho
        for (StockDto dto : map.values()) {
            dto.setCurrentQty(dto.getImportedQty() - dto.getExportedQty());
        }

        // trả về list
        return new ArrayList<>(map.values());
    }

    @Override
    public StockDto getStockByProduct(Long productId) {
        StockDto dto = new StockDto();
        dto.setProductId(productId);

        List<ShopImportDetail> imports = importDetailRepo.findByProductId(productId);
        List<ShopExportDetail> exports = exportDetailRepo.findByProductId(productId);

        if (imports.isEmpty() && exports.isEmpty()) {
            // tùy anh: hoặc trả 0, hoặc báo not found
            throw new NotFoundException("Không có dữ liệu tồn kho cho productId = " + productId);
        }

        int imported = imports.stream()
                .mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 0)
                .sum();
        int exported = exports.stream()
                .mapToInt(e -> e.getQuantity() != null ? e.getQuantity() : 0)
                .sum();

        dto.setImportedQty(imported);
        dto.setExportedQty(exported);
        dto.setCurrentQty(imported - exported);

        return dto;
    }

    
}
