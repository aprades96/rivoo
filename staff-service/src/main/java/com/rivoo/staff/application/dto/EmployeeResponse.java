package com.rivoo.staff.application.dto;

import java.time.Instant;

public record EmployeeResponse(
        String id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String role,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
