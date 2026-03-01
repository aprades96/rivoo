package com.rivoo.staff.application.dto;

public record EmployeeInternalResponse(
        String id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String role,
        boolean active
) {
}
