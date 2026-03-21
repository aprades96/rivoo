package com.rivoo.appointment.domain.port.out;

public interface BillingServicePort {

    /**
     * Returns max appointments per month for the tenant's plan.
     * Returns -1 for unlimited.
     */
    int getMaxAppointmentsPerMonth(String tenantId);
}
