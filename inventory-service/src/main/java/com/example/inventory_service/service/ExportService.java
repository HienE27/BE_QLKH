package com.example.inventory_service.service;

import com.example.inventory_service.dto.ExportDto;
import com.example.inventory_service.dto.ExportRequest;

import java.util.List;

public interface ExportService {

    ExportDto create(ExportRequest req);

    List<ExportDto> getAll();

    ExportDto getById(Long id);

    List<ExportDto> getByStore(Long storeId);

    List<ExportDto> getByOrder(Long orderId);
}
