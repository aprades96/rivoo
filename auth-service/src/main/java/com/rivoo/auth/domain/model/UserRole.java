package com.rivoo.auth.domain.model;

public enum UserRole {
    SALON_OWNER,
    EMPLOYEE;

    public String toKeycloakRole() {
        return "ROLE_" + name();
    }
}
