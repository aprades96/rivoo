package com.rivoo.auth.domain.port.in;

public interface ManageTenantStatusUseCase {
    void disableTenant(String tenantId);
    void setTenantStatus(String tenantId, boolean enabled);
}
