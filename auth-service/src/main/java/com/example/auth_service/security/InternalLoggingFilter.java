package com.example.auth_service.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class InternalLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest req = (HttpServletRequest) request;
            String path = req.getRequestURI();
            if (path != null && path.startsWith("/api/internal/")) {
                String tokenHeader = req.getHeader("X-Activity-Log-Token");
                System.out.println("[InternalLoggingFilter] path=" + path + ", X-Activity-Log-Token present=" + (tokenHeader != null));
            }
        }

        chain.doFilter(request, response);
    }
}


