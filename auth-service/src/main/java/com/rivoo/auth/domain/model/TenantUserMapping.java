package com.rivoo.auth.domain.model;

import java.time.Instant;

public class TenantUserMapping {

    private Long id;
    private String tenantId;
    private String keycloakUserId;
    private UserRole role;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public TenantUserMapping() {
    }

    public TenantUserMapping(String tenantId, String keycloakUserId, UserRole role) {
        this.tenantId = tenantId;
        this.keycloakUserId = keycloakUserId;
        this.role = role;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getKeycloakUserId() {
        return keycloakUserId;
    }

    public void setKeycloakUserId(String keycloakUserId) {
        this.keycloakUserId = keycloakUserId;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
