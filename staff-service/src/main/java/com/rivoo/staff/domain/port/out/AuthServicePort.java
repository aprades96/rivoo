package com.rivoo.staff.domain.port.out;

public interface AuthServicePort {

    String registerEmployee(String tenantId, String email, String password,
                            String firstName, String lastName, String salonName);

    void deleteUser(String keycloakUserId);
}
