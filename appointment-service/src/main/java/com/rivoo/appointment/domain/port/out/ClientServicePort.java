package com.rivoo.appointment.domain.port.out;

public interface ClientServicePort {

    ClientInfo getClient(String tenantId, String clientExternalId);

    record ClientInfo(String externalId, String firstName, String lastName, String email, String phone, boolean active) {
        public String fullName() {
            return firstName + " " + lastName;
        }
    }
}
