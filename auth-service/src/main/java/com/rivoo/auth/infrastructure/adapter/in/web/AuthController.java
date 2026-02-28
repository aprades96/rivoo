package com.rivoo.auth.infrastructure.adapter.in.web;

import com.rivoo.auth.application.dto.*;
import com.rivoo.auth.domain.port.in.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final RegisterOwnerUseCase registerOwnerUseCase;
    private final RegisterEmployeeUseCase registerEmployeeUseCase;
    private final ManageTenantStatusUseCase manageTenantStatusUseCase;
    private final UpdateTenantAttributeUseCase updateTenantAttributeUseCase;
    private final ListTenantUsersUseCase listTenantUsersUseCase;

    public AuthController(RegisterOwnerUseCase registerOwnerUseCase,
                          RegisterEmployeeUseCase registerEmployeeUseCase,
                          ManageTenantStatusUseCase manageTenantStatusUseCase,
                          UpdateTenantAttributeUseCase updateTenantAttributeUseCase,
                          ListTenantUsersUseCase listTenantUsersUseCase) {
        this.registerOwnerUseCase = registerOwnerUseCase;
        this.registerEmployeeUseCase = registerEmployeeUseCase;
        this.manageTenantStatusUseCase = manageTenantStatusUseCase;
        this.updateTenantAttributeUseCase = updateTenantAttributeUseCase;
        this.listTenantUsersUseCase = listTenantUsersUseCase;
    }

    @PostMapping("/api/internal/auth/register-owner")
    public ResponseEntity<RegisterOwnerResponse> registerOwner(
            @Valid @RequestBody RegisterOwnerRequest request) {
        log.info("POST /api/internal/auth/register-owner for tenant {}", request.tenantId());
        RegisterOwnerResponse response = registerOwnerUseCase.registerOwner(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/internal/auth/register-employee")
    public ResponseEntity<RegisterEmployeeResponse> registerEmployee(
            @Valid @RequestBody RegisterEmployeeRequest request) {
        log.info("POST /api/internal/auth/register-employee for tenant {}", request.tenantId());
        RegisterEmployeeResponse response = registerEmployeeUseCase.registerEmployee(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/api/internal/auth/tenants/{tenantId}/disable")
    public ResponseEntity<Void> disableTenant(@PathVariable String tenantId) {
        log.info("PUT /api/internal/auth/tenants/{}/disable", tenantId);
        manageTenantStatusUseCase.disableTenant(tenantId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/internal/auth/tenants/{tenantId}/attributes")
    public ResponseEntity<Void> updateTenantAttributes(
            @PathVariable String tenantId,
            @Valid @RequestBody UpdateAttributeRequest request) {
        log.info("PUT /api/internal/auth/tenants/{}/attributes", tenantId);
        updateTenantAttributeUseCase.updateTenantAttributes(tenantId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/internal/auth/tenants/{tenantId}/users")
    public ResponseEntity<List<TenantUserResponse>> listTenantUsers(@PathVariable String tenantId) {
        log.debug("GET /api/internal/auth/tenants/{}/users", tenantId);
        List<TenantUserResponse> users = listTenantUsersUseCase.listTenantUsers(tenantId);
        return ResponseEntity.ok(users);
    }

    @PutMapping("/api/internal/admin/tenants/{tenantId}/status")
    public ResponseEntity<Void> setTenantStatus(
            @PathVariable String tenantId,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", false);
        log.info("PUT /api/internal/admin/tenants/{}/status enabled={}", tenantId, enabled);
        manageTenantStatusUseCase.setTenantStatus(tenantId, enabled);
        return ResponseEntity.noContent().build();
    }
}
