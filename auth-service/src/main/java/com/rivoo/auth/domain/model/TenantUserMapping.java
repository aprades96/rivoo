package com.rivoo.auth.domain.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
public class TenantUserMapping {

    private Long id;
    private String tenantId;
    private String keycloakUserId;
    private UserRole role;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public TenantUserMapping(String tenantId, String keycloakUserId, UserRole role) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(keycloakUserId, "keycloakUserId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        this.tenantId = tenantId;
        this.keycloakUserId = keycloakUserId;
        this.role = role;
        this.active = true;
    }
}
