package com.rivoo.client.application.dto;

import java.time.Instant;
import java.util.List;

public record ClientExportResponse(
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
        Instant gdprConsentAt,
        Instant createdAt,
        List<String> appointments
) {
}
