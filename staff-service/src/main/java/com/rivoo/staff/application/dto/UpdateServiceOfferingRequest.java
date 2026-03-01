package com.rivoo.staff.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateServiceOfferingRequest(
        @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @Min(1) Integer durationMinutes,
        @DecimalMin("0.00") BigDecimal price,
        String currency
) {
}
