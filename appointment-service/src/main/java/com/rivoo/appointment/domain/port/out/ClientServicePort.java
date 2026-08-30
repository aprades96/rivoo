package com.rivoo.appointment.domain.port.out;

import java.time.Instant;

public interface ClientServicePort {

    ClientInfo getClient(String tenantId, String clientExternalId);

    ClientInfo findOrCreateClient(String tenantId, String firstName, String lastName, String email, String phone);

    void registerVisit(String tenantId, String clientExternalId, Instant visitAt);

    record ClientInfo(String externalId, String firstName, String lastName, String email, String phone, boolean active) {
        public String fullName() {
            return firstName + " " + lastName;
        }
    }
}
