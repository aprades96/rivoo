package com.rivoo.staff.application.dto;

import java.math.BigDecimal;

public record ServiceOfferingPublicResponse(
        String id,
        String name,
        String description,
        int durationMinutes,
        BigDecimal price,
        String currency
) {
}
