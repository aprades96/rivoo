package com.rivoo.staff.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateServiceOfferingRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull @Min(1) Integer durationMinutes,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Size(max = 100) String category
) {
}
