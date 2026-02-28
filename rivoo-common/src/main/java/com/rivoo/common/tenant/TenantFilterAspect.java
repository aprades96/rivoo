package com.rivoo.common.tenant;

import jakarta.persistence.EntityManager;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
public class TenantFilterAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantFilterAspect.class);

    private final EntityManager entityManager;

    public TenantFilterAspect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Before("execution(* com.rivoo..infrastructure.adapter.out.persistence..*Repository*.*(..))")
    public void activateTenantFilter() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
            log.trace("Tenant filter activated for tenant: {}", tenantId);
        }
        // tenantId == null → PLATFORM_ADMIN → no filter (cross-tenant access)
    }
}
