package com.example.auth_service.repository;

import com.example.auth_service.entity.AdUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdUserRepository extends JpaRepository<AdUser, Long> {
    Optional<AdUser> findByUsername(String username);
}