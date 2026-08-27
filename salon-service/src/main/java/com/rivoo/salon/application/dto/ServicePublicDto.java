package com.rivoo.salon.application.dto;

import java.math.BigDecimal;

public record ServicePublicDto(
        String id,
        String name,
        String description,
        int durationMinutes,
        BigDecimal price,
        String currency
) {
}
