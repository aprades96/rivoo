package com.rivoo.staff.application.dto;

import java.time.Instant;

public record EmployeeResponse(
        String id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String jobTitle,
        String colorHex,
        String role,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
}
