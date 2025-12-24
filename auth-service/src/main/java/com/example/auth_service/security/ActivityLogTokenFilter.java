package com.example.auth_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class ActivityLogTokenFilter extends OncePerRequestFilter {

    @Value("${ACTIVITY_LOG_SERVICE_TOKEN:}")
    private String activityLogServiceToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/api/internal/")) {
            String token = request.getHeader("X-Activity-Log-Token");
            System.out.println("[ActivityLogTokenFilter] headerPresent=" + (token != null) + ", configured=" + (activityLogServiceToken != null && !activityLogServiceToken.isBlank()));
            if (activityLogServiceToken != null && !activityLogServiceToken.isBlank() && token != null && token.equals(activityLogServiceToken)) {
                System.out.println("[ActivityLogTokenFilter] token valid, setting authentication");
                // set a simple authenticated principal for downstream code
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        "service-account", null, List.of(new SimpleGrantedAuthority("ROLE_SYSTEM")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                System.out.println("[ActivityLogTokenFilter] token invalid or missing");
            }
        }

        filterChain.doFilter(request, response);
    }
}


