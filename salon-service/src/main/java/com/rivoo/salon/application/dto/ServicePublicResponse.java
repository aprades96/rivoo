package com.rivoo.salon.application.dto;

import java.math.BigDecimal;

public record ServicePublicResponse(
        String id,
        String name,
        String description,
        int durationMinutes,
        BigDecimal price,
        String currency
) {
}
