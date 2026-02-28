package com.rivoo.auth.domain.port.out;

import com.rivoo.auth.domain.model.TenantUserMapping;

import java.util.List;

public interface TenantUserMappingPort {
    TenantUserMapping save(TenantUserMapping mapping);
    List<TenantUserMapping> findByTenantId(String tenantId);
    void updateActiveStatusByTenantId(String tenantId, boolean active);
}
