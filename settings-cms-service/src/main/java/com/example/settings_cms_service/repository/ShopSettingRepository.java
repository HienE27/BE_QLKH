package com.example.settings_cms_service.repository;

import com.example.settings_cms_service.entity.ShopSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShopSettingRepository extends JpaRepository<ShopSetting, Long> {
    Optional<ShopSetting> findBySettingKey(String settingKey);
}