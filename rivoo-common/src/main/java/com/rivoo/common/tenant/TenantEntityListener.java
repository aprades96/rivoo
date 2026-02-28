package com.rivoo.common.tenant;

import jakarta.persistence.PrePersist;

public class TenantEntityListener {

    @PrePersist
    public void setTenantId(Object entity) {
        if (entity instanceof TenantAwareEntity tenantAware) {
            if (tenantAware.getTenantId() == null) {
                String currentTenantId = TenantContext.getCurrentTenantId();
                if (currentTenantId != null) {
                    tenantAware.setTenantId(currentTenantId);
                }
            }
        }
    }
}
