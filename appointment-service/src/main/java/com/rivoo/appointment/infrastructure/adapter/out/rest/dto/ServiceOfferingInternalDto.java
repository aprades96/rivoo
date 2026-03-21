package com.rivoo.appointment.infrastructure.adapter.out.rest.dto;

import java.math.BigDecimal;

public record ServiceOfferingInternalDto(
        String id,
        String name,
        BigDecimal price,
        int durationMinutes,
        boolean active
) {
}
