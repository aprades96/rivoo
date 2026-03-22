package com.rivoo.salon.domain.port.out;

public interface BillingServicePort {

    void createSubscription(String tenantId, String ownerEmail, String salonName);
}
