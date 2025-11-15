package com.example.auth_service.service.impl;

import com.example.auth_service.dto.LoginRequest;
import com.example.auth_service.dto.LoginResponse;
import com.example.auth_service.dto.UserDto;
import com.example.auth_service.entity.AdUser;
import com.example.auth_service.repository.AdUserRepository;
import com.example.auth_service.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements com.example.auth_service.service.AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AdUserRepository userRepo;

    @Override
    public LoginResponse login(LoginRequest req) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        req.getUsername(), req.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        AdUser user = userRepo.findByUsername(req.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserDto userDto = UserDto.fromEntity(user);

        String token = jwtService.generateToken(
                user.getUsername(),
                userDto.getRoles()
        );

        LoginResponse res = new LoginResponse();
        res.setToken(token);
        res.setUser(userDto);
        return res;
    }

    @Override
    public UserDto getCurrentUser() {
        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        AdUser user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return UserDto.fromEntity(user);
    }
}