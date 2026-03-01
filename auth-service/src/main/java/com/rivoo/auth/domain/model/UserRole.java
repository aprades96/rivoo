package com.rivoo.auth.domain.model;

public enum UserRole {
    SALON_OWNER,
    EMPLOYEE;

    public String toKeycloakRole() {
        return "ROLE_" + name();
    }

    public static UserRole fromKeycloakRole(String keycloakRole) {
        if (keycloakRole == null) {
            throw new IllegalArgumentException("keycloakRole must not be null");
        }
        String name = keycloakRole.startsWith("ROLE_") ? keycloakRole.substring(5) : keycloakRole;
        return UserRole.valueOf(name);
    }
}
