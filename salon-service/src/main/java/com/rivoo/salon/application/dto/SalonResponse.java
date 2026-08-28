package com.rivoo.salon.application.dto;

import java.time.Instant;

public record SalonResponse(
        String id,
        String name,
        String slug,
        String email,
        String phone,
        String description,
        String logoUrl,
        String primaryColor,
        String addressStreet,
        String addressCity,
        String addressPostalCode,
        String timezone,
        String currency,
        String subscriptionPlan,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant onboardingCompletedAt
) {
}
