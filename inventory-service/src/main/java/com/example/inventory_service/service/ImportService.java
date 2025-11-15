package com.example.inventory_service.service;

import com.example.inventory_service.dto.ImportDto;
import com.example.inventory_service.dto.ImportRequest;

import java.util.List;

public interface ImportService {

    ImportDto create(ImportRequest req);

    List<ImportDto> getAll();

    ImportDto getById(Long id);

    List<ImportDto> getByStore(Long storeId);
}
