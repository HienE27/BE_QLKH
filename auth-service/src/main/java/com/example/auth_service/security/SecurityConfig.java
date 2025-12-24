// src/main/java/com/example/auth_service/security/SecurityConfig.java
package com.example.auth_service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;



@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ActivityLogTokenFilter activityLogTokenFilter;

    @Value("${app.cors.enabled:false}")
    private boolean corsEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .contentTypeOptions(contentTypeOptions -> {})
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(31536000)
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        // cho phép login / register, v.v...
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/forgot-password").permitAll()
                        .requestMatchers("/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/auth/verify-email").permitAll()
                        .requestMatchers("/api/auth/resend-verification").permitAll()
                        // Profile endpoints yêu cầu authentication
                        .requestMatchers("/api/auth/profile").authenticated()
                        // User management endpoints
                        .requestMatchers("/api/users/**").authenticated()
                        // Role management endpoints
                        .requestMatchers("/api/roles/**").authenticated()
                        // Permission management endpoints
                        .requestMatchers("/api/permissions/**").authenticated()
                        // Allow POST to activity-logs from services (token-based); other activity-log routes require auth
                        // Internal activity-log endpoint (for services using X-Activity-Log-Token)
                        .requestMatchers("/api/internal/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/activity-logs", "/api/activity-logs/**").permitAll()
                        // Activity logs endpoints (other methods)
                        .requestMatchers("/api/activity-logs/**").authenticated()
                        // nếu mai mốt auth-service có API khác thì yêu cầu auth
                        .anyRequest().authenticated()
                )
                // register activity-log token filter early (before standard username/password filter)
                .addFilterBefore(activityLogTokenFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .userDetailsService(userDetailsService);

        // NOTE: When requests go through api-gateway, gateway already handles CORS.
        // Enabling CORS here can duplicate Access-Control-Allow-Origin headers.
        if (corsEnabled) {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
        } else {
            http.cors(cors -> cors.disable());
        }

        return http.build();
    }

    /**
     * Separate, higher-priority security chain for internal endpoints so that we can
     * permit anonymous access and still run the ActivityLogTokenFilter before other
     * authentication filters. This avoids the main JWT filter or method security
     * preventing the internal token flow.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/internal/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                // ensure activity token filter runs for internal calls
                .addFilterBefore(activityLogTokenFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

        // do not enable CORS here for internal chain
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow frontend origins
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",
                "http://localhost:3001",
                "http://127.0.0.1:3000",
                "http://127.0.0.1:3001"
        ));
        // Allow common HTTP methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        // Allow all headers
        configuration.setAllowedHeaders(Arrays.asList("*"));
        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);
        // Cache preflight response for 1 hour
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
        //return org.springframework.security.crypto.password.NoOpPasswordEncoder.getInstance();
    }

    /**
     * Register ActivityLogTokenFilter as a servlet filter to guarantee it runs
     * before Spring Security filter chain. This ensures internal token authentication
     * is applied early for requests to /api/internal/*.
     */
    @Bean
    public FilterRegistrationBean<ActivityLogTokenFilter> activityLogFilterRegistration(ActivityLogTokenFilter filter) {
        FilterRegistrationBean<ActivityLogTokenFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(0); // highest priority
        registration.addUrlPatterns("/api/internal/*");
        registration.setName("ActivityLogTokenFilterRegistration");
        return registration;
    }

    /**
     * Completely bypass Spring Security filter chain for internal endpoints so they
     * are not processed by JwtAuthenticationFilter or other security filters.
     * This allows the servlet filter above to authenticate the request via header token.
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers("/api/internal/**");
    }

    // Register the internal logging filter (for debugging) so we can confirm servlet filters execute
    @Bean
    public FilterRegistrationBean<InternalLoggingFilter> internalLoggingFilterRegistration(@Autowired @Lazy InternalLoggingFilter filter) {
        FilterRegistrationBean<InternalLoggingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(0); // run very early
        registration.addUrlPatterns("/api/internal/*");
        registration.setName("InternalLoggingFilterRegistration");
        return registration;
    }
}
