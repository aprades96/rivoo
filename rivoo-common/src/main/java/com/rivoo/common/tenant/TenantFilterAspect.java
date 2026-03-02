package com.rivoo.common.tenant;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;

@Slf4j
@RequiredArgsConstructor
@Aspect
public class TenantFilterAspect {

    private final EntityManager entityManager;

    @Before("execution(* com.rivoo..infrastructure.adapter.out.persistence..*Repository*.*(..))")
    public void activateTenantFilter() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            try {
                Session session = entityManager.unwrap(Session.class);
                session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
                log.atTrace().log("Tenant filter activated");
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to activate tenant filter for tenant: " + tenantId, e);
            }
        }
        // tenantId == null → PLATFORM_ADMIN → no filter (cross-tenant access)
    }
}
