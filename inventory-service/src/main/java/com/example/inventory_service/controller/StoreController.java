package com.example.inventory_service.controller;

import com.example.inventory_service.common.ApiResponse;
import com.example.inventory_service.dto.StoreDto;
import com.example.inventory_service.dto.StoreRequest;
import com.example.inventory_service.service.StoreService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreService service;

    public StoreController(StoreService service) {
        this.service = service;
    }

    @GetMapping("/search")
    public Page<StoreDto> search(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            Pageable pageable) {
        return service.search(code, name, pageable);
    }

    /**
     * @deprecated Use paginated version {@link #getAllPaged(int, int)} instead.
     * This endpoint returns a limited list (max 100 stores) and may not return all results.
     */
    @Deprecated
    @GetMapping
    public ApiResponse<List<StoreDto>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @GetMapping("/paged")
    public ApiResponse<Page<StoreDto>> getAllPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<StoreDto> data = service.getAll(pageable);
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<StoreDto> getById(@PathVariable Long id) {
        return ApiResponse.ok(service.getById(id));
    }

    @PostMapping
    public ApiResponse<StoreDto> create(@RequestBody StoreRequest req) {
        return ApiResponse.ok("Created", service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<StoreDto> update(@PathVariable Long id,
                                        @RequestBody StoreRequest req) {
        return ApiResponse.ok("Updated", service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}
