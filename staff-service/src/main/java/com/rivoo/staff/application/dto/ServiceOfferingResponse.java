package com.rivoo.staff.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ServiceOfferingResponse(
        String id,
        String name,
        String description,
        int durationMinutes,
        BigDecimal price,
        String currency,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
