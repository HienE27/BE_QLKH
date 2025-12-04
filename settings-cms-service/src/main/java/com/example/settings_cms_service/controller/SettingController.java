package com.example.settings_cms_service.controller;

import com.example.settings_cms_service.dto.SettingRequest;
import com.example.settings_cms_service.entity.ShopSetting;
import com.example.settings_cms_service.service.SettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;

    @GetMapping
    public List<ShopSetting> getAll() {
        return settingService.getAll();
    }

    @GetMapping("/{key}")
    public ShopSetting getByKey(@PathVariable String key) {
        return settingService.getByKey(key);
    }

    @PostMapping
    public ShopSetting createOrUpdate(@Valid @RequestBody SettingRequest request) {
        return settingService.createOrUpdate(request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        settingService.delete(id);
    }
}
