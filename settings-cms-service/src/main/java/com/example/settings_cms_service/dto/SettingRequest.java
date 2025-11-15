package com.example.settings_cms_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SettingRequest {
    @NotBlank
    private String key;
    private String value;
    private String description;
}
