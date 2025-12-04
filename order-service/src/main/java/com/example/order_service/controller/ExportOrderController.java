package com.example.order_service.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;

/**
 * Controller cho Lệnh xuất kho (Export Orders)
 * Proxy sang inventory-service
 */
@RestController
@RequestMapping("/api/orders/exports")
public class ExportOrderController {

    private final RestTemplate restTemplate;

    @Value("${inventory.service.url:http://inventory-service}")
    private String inventoryServiceUrl;

    public ExportOrderController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String url = buildUrl("/api/orders/exports", status, code, from, to);

        HttpHeaders headers = new HttpHeaders();
        if (authHeader != null) {
            headers.set("Authorization", authHeader);
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        return restTemplate.exchange(url, HttpMethod.GET, entity, Object.class);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String url = inventoryServiceUrl + "/api/orders/exports/" + id;

        HttpHeaders headers = new HttpHeaders();
        if (authHeader != null) {
            headers.set("Authorization", authHeader);
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        return restTemplate.exchange(url, HttpMethod.GET, entity, Object.class);
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody Object request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String url = inventoryServiceUrl + "/api/orders/exports";

        HttpHeaders headers = new HttpHeaders();
        if (authHeader != null) {
            headers.set("Authorization", authHeader);
        }
        headers.set("Content-Type", "application/json");

        HttpEntity<Object> entity = new HttpEntity<>(request, headers);

        return restTemplate.exchange(url, HttpMethod.POST, entity, Object.class);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestBody Object request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String url = inventoryServiceUrl + "/api/orders/exports/" + id;

        HttpHeaders headers = new HttpHeaders();
        if (authHeader != null) {
            headers.set("Authorization", authHeader);
        }
        headers.set("Content-Type", "application/json");

        HttpEntity<Object> entity = new HttpEntity<>(request, headers);

        return restTemplate.exchange(url, HttpMethod.PUT, entity, Object.class);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String url = inventoryServiceUrl + "/api/orders/exports/" + id + "/approve";

        HttpHeaders headers = new HttpHeaders();
        if (authHeader != null) {
            headers.set("Authorization", authHeader);
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        return restTemplate.exchange(url, HttpMethod.POST, entity, Object.class);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String url = inventoryServiceUrl + "/api/orders/exports/" + id + "/cancel";

        HttpHeaders headers = new HttpHeaders();
        if (authHeader != null) {
            headers.set("Authorization", authHeader);
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        return restTemplate.exchange(url, HttpMethod.POST, entity, Object.class);
    }

    private String buildUrl(String basePath, String status, String code, LocalDate from, LocalDate to) {
        StringBuilder url = new StringBuilder(inventoryServiceUrl + basePath);
        boolean hasParam = false;

        if (status != null) {
            url.append("?status=").append(status);
            hasParam = true;
        }
        if (code != null) {
            url.append(hasParam ? "&" : "?").append("code=").append(code);
            hasParam = true;
        }
        if (from != null) {
            url.append(hasParam ? "&" : "?").append("from=").append(from);
            hasParam = true;
        }
        if (to != null) {
            url.append(hasParam ? "&" : "?").append("to=").append(to);
        }

        return url.toString();
    }
}
