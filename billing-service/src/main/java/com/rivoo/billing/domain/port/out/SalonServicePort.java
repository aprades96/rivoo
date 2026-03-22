package com.rivoo.billing.domain.port.out;

public interface SalonServicePort {

    void updateSalonStatus(String tenantId, String status);
}
