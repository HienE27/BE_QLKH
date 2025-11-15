package com.example.inventory_service.service.impl;

import com.example.inventory_service.dto.ExportDetailDto;
import com.example.inventory_service.dto.ExportDetailRequest;
import com.example.inventory_service.dto.ExportDto;
import com.example.inventory_service.dto.ExportRequest;
import com.example.inventory_service.entity.ShopExport;
import com.example.inventory_service.entity.ShopExportDetail;
import com.example.inventory_service.exception.NotFoundException;
import com.example.inventory_service.repository.ShopExportDetailRepository;
import com.example.inventory_service.repository.ShopExportRepository;
import com.example.inventory_service.service.ExportService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class ExportServiceImpl implements ExportService {

    private final ShopExportRepository exportRepo;
    private final ShopExportDetailRepository exportDetailRepo;

    public ExportServiceImpl(ShopExportRepository exportRepo,
                             ShopExportDetailRepository exportDetailRepo) {
        this.exportRepo = exportRepo;
        this.exportDetailRepo = exportDetailRepo;
    }

    @Override
    public ExportDto create(ExportRequest req) {
        ShopExport ex = new ShopExport();
        ex.setStoreId(req.getStoreId());
        ex.setUserId(req.getUserId());
        ex.setOrderId(req.getOrderId());
        ex.setNote(req.getNote());
        ex.setDescription(req.getDescription());
        ex.setExportsDate(new Date());
        ex.setCreatedAt(new Date());
        ex.setUpdatedAt(new Date());
        ex = exportRepo.save(ex);

        if (req.getDetails() != null) {
            for (ExportDetailRequest d : req.getDetails()) {
                ShopExportDetail ed = new ShopExportDetail();
                ed.setExportId(ex.getId());
                ed.setImportDetailId(d.getImportDetailId());
                ed.setProductId(d.getProductId());
                ed.setQuantity(d.getQuantity());
                ed.setUnitPrice(d.getUnitPrice());
                exportDetailRepo.save(ed);
            }
        }

        List<ShopExportDetail> details = exportDetailRepo.findByExportId(ex.getId());
        return toDto(ex, details);
    }

    @Override
    public List<ExportDto> getAll() {
        return exportRepo.findAll().stream()
                .map(ex -> {
                    List<ShopExportDetail> details = exportDetailRepo.findByExportId(ex.getId());
                    return toDto(ex, details);
                })
                .toList();
    }

    @Override
    public ExportDto getById(Long id) {
        ShopExport ex = exportRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Export not found: " + id));
        List<ShopExportDetail> details = exportDetailRepo.findByExportId(ex.getId());
        return toDto(ex, details);
    }

    @Override
    public List<ExportDto> getByStore(Long storeId) {
        return exportRepo.findByStoreId(storeId).stream()
                .map(ex -> {
                    List<ShopExportDetail> details = exportDetailRepo.findByExportId(ex.getId());
                    return toDto(ex, details);
                })
                .toList();
    }

    @Override
    public List<ExportDto> getByOrder(Long orderId) {
        return exportRepo.findByOrderId(orderId).stream()
                .map(ex -> {
                    List<ShopExportDetail> details = exportDetailRepo.findByExportId(ex.getId());
                    return toDto(ex, details);
                })
                .toList();
    }

    // helper mapping
    private ExportDto toDto(ShopExport ex, List<ShopExportDetail> details) {
        ExportDto dto = new ExportDto();
        dto.setId(ex.getId());
        dto.setStoreId(ex.getStoreId());
        dto.setUserId(ex.getUserId());
        dto.setOrderId(ex.getOrderId());
        dto.setNote(ex.getNote());
        dto.setDescription(ex.getDescription());
        dto.setExportsDate(ex.getExportsDate());
        dto.setCreatedAt(ex.getCreatedAt());
        dto.setUpdatedAt(ex.getUpdatedAt());

        List<ExportDetailDto> dts = new ArrayList<>();
        if (details != null) {
            for (ShopExportDetail d : details) {
                ExportDetailDto ed = new ExportDetailDto();
                ed.setId(d.getId());
                ed.setImportDetailId(d.getImportDetailId());
                ed.setProductId(d.getProductId());
                ed.setQuantity(d.getQuantity());
                ed.setUnitPrice(d.getUnitPrice());
                dts.add(ed);
            }
        }
        dto.setDetails(dts);
        return dto;
    }
}
