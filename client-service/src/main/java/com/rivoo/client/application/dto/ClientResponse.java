package com.rivoo.client.application.dto;

import java.time.Instant;

public record ClientResponse(
        String id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String gender,
        String source,
        String notes,
        int totalVisits,
        Instant lastVisitAt,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
