package com.example.inventory_service.service.impl;

import com.example.inventory_service.dto.ImportDetailDto;
import com.example.inventory_service.dto.ImportDetailRequest;
import com.example.inventory_service.dto.ImportDto;
import com.example.inventory_service.dto.ImportRequest;
import com.example.inventory_service.entity.ShopImport;
import com.example.inventory_service.entity.ShopImportDetail;
import com.example.inventory_service.exception.NotFoundException;
import com.example.inventory_service.repository.ShopImportDetailRepository;
import com.example.inventory_service.repository.ShopImportRepository;
import com.example.inventory_service.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImportServiceImpl implements ImportService {

    private final ShopImportRepository importRepo;
    private final ShopImportDetailRepository detailRepo;

    @Override
    public ImportDto create(ImportRequest req) {
        // 1. Tạo phiếu nhập
        ShopImport im = new ShopImport();
        im.setStoreId(req.getStoreId());
        im.setUserId(req.getUserId());
        im.setNote(req.getNote());
        im.setCreatedAt(new Date());
        im.setUpdatedAt(new Date());
        im = importRepo.save(im);

        // 2. Lưu chi tiết + cập nhật tồn kho
        if (req.getDetails() != null) {
            for (ImportDetailRequest d : req.getDetails()) {
                ShopImportDetail det = new ShopImportDetail();
                det.setImportId(im.getId());
                det.setProductId(d.getProductId());
                det.setQuantity(d.getQuantity());
                det.setUnitPrice(d.getUnitPrice());
                detailRepo.save(det);

            }
        }

        // 3. Load lại details
        List<ShopImportDetail> details = detailRepo.findByImportId(im.getId());
        return toDto(im, details);
    }

    @Override
    public List<ImportDto> getAll() {
        return importRepo.findAll().stream()
                .map(im -> {
                    List<ShopImportDetail> details = detailRepo.findByImportId(im.getId());
                    return toDto(im, details);
                })
                .toList();
    }

    @Override
    public ImportDto getById(Long id) {
        ShopImport im = importRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Import not found: " + id));
        List<ShopImportDetail> details = detailRepo.findByImportId(im.getId());
        return toDto(im, details);
    }

    @Override
    public List<ImportDto> getByStore(Long storeId) {
        return importRepo.findByStoreId(storeId).stream()
                .map(im -> {
                    List<ShopImportDetail> details = detailRepo.findByImportId(im.getId());
                    return toDto(im, details);
                })
                .toList();
    }

    // ================= helper ===================

    private ImportDto toDto(ShopImport im, List<ShopImportDetail> details) {
        ImportDto dto = new ImportDto();
        dto.setId(im.getId());
        dto.setStoreId(im.getStoreId());
        dto.setUserId(im.getUserId());
        dto.setNote(im.getNote());
        dto.setCreatedAt(im.getCreatedAt());
        dto.setUpdatedAt(im.getUpdatedAt());

        List<ImportDetailDto> detailDtos = new ArrayList<>();
        if (details != null) {
            for (ShopImportDetail d : details) {
                ImportDetailDto dd = new ImportDetailDto();
                dd.setId(d.getId());
                dd.setProductId(d.getProductId());
                dd.setQuantity(d.getQuantity());
                dd.setUnitPrice(d.getUnitPrice());
                detailDtos.add(dd);
            }
        }
        dto.setDetails(detailDtos);

        return dto;
    }
}
