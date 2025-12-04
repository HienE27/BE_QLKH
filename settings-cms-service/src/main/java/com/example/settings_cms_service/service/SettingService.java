package com.example.settings_cms_service.service;

import com.example.settings_cms_service.dto.SettingRequest;
import com.example.settings_cms_service.entity.ShopSetting;

import java.util.List;

public interface SettingService {
    List<ShopSetting> getAll();
    ShopSetting getByKey(String key);
    ShopSetting createOrUpdate(SettingRequest request);
    void delete(Long id);
}
