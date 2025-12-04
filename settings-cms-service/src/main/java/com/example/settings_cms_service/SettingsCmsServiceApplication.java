package com.example.settings_cms_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient  
public class SettingsCmsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SettingsCmsServiceApplication.class, args);
    }
}