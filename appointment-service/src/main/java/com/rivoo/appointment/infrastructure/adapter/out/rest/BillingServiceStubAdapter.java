package com.rivoo.appointment.infrastructure.adapter.out.rest;

import com.rivoo.appointment.domain.port.out.BillingServicePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stub adapter until billing-service is implemented (Fase 7).
 * Returns -1 (unlimited) for all plan limits.
 */
@Slf4j
@Component
public class BillingServiceStubAdapter implements BillingServicePort {

    @Override
    public int getMaxAppointmentsPerMonth(String tenantId) {
        log.atInfo().addKeyValue("tenantId", tenantId).log("Billing stub: returning unlimited appointments");
        return -1;
    }
}
