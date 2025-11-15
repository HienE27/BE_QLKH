package com.example.auth_service.security;

import com.example.auth_service.entity.AdRole;
import com.example.auth_service.entity.AdUser;
import com.example.auth_service.repository.AdUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AdUserRepository userRepository;

    public CustomUserDetailsService(AdUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        AdUser user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + username));

        boolean enabled = user.getActive() == null || Boolean.TRUE.equals(user.getActive());

        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(AdRole::getRoleCode)
                .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                .toList();

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                enabled,
                true,
                true,
                true,
                authorities
        );
    }
}