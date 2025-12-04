package com.example.auth_service.repository;

import com.example.auth_service.entity.AdRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdRoleRepository extends JpaRepository<AdRole, Long> {
}