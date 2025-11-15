package com.example.auth_service.security;

import com.example.auth_service.entity.AdUser;
import com.example.auth_service.repository.AdUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AdUserRepository userRepo;

    public CustomUserDetailsService(AdUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        AdUser user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        boolean enabled = user.getActive() == null || Boolean.TRUE.equals(user.getActive());

        List<SimpleGrantedAuthority> authorities =
                user.getRoles() == null ? List.of() :
                        user.getRoles().stream()
                                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getCode()))
                                .toList();

        return new User(user.getUsername(), user.getPassword(), enabled,
                true, true, true, authorities);
    }
}