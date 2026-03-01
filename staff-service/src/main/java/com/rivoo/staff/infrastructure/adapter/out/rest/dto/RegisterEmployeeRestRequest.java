package com.rivoo.staff.infrastructure.adapter.out.rest.dto;

public record RegisterEmployeeRestRequest(
        String tenantId,
        String email,
        String password,
        String firstName,
        String lastName,
        String salonName
) {
}
