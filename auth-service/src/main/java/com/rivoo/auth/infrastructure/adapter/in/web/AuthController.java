package com.rivoo.auth.infrastructure.adapter.in.web;

import com.rivoo.auth.application.dto.*;
import com.rivoo.auth.domain.port.in.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
public class AuthController {

    private final RegisterOwnerUseCase registerOwnerUseCase;
    private final RegisterEmployeeUseCase registerEmployeeUseCase;
    private final ManageTenantStatusUseCase manageTenantStatusUseCase;
    private final UpdateTenantAttributeUseCase updateTenantAttributeUseCase;
    private final ListTenantUsersUseCase listTenantUsersUseCase;

    @PostMapping("/api/internal/auth/register-owner")
    public ResponseEntity<RegisterOwnerResponse> registerOwner(
            @Valid @RequestBody RegisterOwnerRequest request) {
        log.atInfo().log("POST /api/internal/auth/register-owner");
        RegisterOwnerResponse response = registerOwnerUseCase.registerOwner(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/internal/auth/register-employee")
    public ResponseEntity<RegisterEmployeeResponse> registerEmployee(
            @Valid @RequestBody RegisterEmployeeRequest request) {
        log.atInfo().log("POST /api/internal/auth/register-employee");
        RegisterEmployeeResponse response = registerEmployeeUseCase.registerEmployee(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/api/internal/auth/tenants/{tenantId}/disable")
    public ResponseEntity<Void> disableTenant(@PathVariable String tenantId) {
        log.atInfo().log("PUT /api/internal/auth/tenants/disable");
        manageTenantStatusUseCase.disableTenant(tenantId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/internal/auth/tenants/{tenantId}/attributes")
    public ResponseEntity<Void> updateTenantAttributes(
            @PathVariable String tenantId,
            @Valid @RequestBody UpdateAttributeRequest request) {
        log.atInfo().log("PUT /api/internal/auth/tenants/attributes");
        updateTenantAttributeUseCase.updateTenantAttributes(tenantId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/internal/auth/tenants/{tenantId}/users")
    public ResponseEntity<List<TenantUserResponse>> listTenantUsers(@PathVariable String tenantId) {
        log.atDebug().log("GET /api/internal/auth/tenants/users");
        List<TenantUserResponse> users = listTenantUsersUseCase.listTenantUsers(tenantId);
        return ResponseEntity.ok(users);
    }

    @PutMapping("/api/internal/admin/tenants/{tenantId}/status")
    public ResponseEntity<Void> setTenantStatus(
            @PathVariable String tenantId,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", false);
        log.atInfo().addKeyValue("enabled", enabled).log("PUT /api/internal/admin/tenants/status");
        manageTenantStatusUseCase.setTenantStatus(tenantId, enabled);
        return ResponseEntity.noContent().build();
    }
}
