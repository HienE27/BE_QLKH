package com.example.settings_cms_service.impl;

import com.example.settings_cms_service.dto.SettingRequest;
import com.example.settings_cms_service.entity.ShopSetting;
import com.example.settings_cms_service.repository.ShopSettingRepository;
import com.example.settings_cms_service.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettingServiceImpl implements SettingService {

    private final ShopSettingRepository repo;

    @Override
    public List<ShopSetting> getAll() {
        return repo.findAll();
    }

    @Override
    public ShopSetting getByKey(String key) {
        return repo.findBySettingKey(key)
                .orElseThrow(() -> new RuntimeException("Setting not found"));
    }

    @Override
    public ShopSetting createOrUpdate(SettingRequest request) {
        ShopSetting setting = repo.findBySettingKey(request.getKey())
                .orElse(ShopSetting.builder()
                        .settingKey(request.getKey())
                        .createdAt(LocalDateTime.now())
                        .build());
        setting.setValue(request.getValue());
        setting.setDescription(request.getDescription());
        setting.setUpdatedAt(LocalDateTime.now());
        return repo.save(setting);
    }

    @Override
    public void delete(Long id) {
        repo.deleteById(id);
    }
}
