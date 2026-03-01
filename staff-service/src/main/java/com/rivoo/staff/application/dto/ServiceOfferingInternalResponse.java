package com.rivoo.staff.application.dto;

import java.math.BigDecimal;

public record ServiceOfferingInternalResponse(
        String id,
        String name,
        int durationMinutes,
        BigDecimal price,
        String currency,
        boolean active
) {
}
