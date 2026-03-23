package com.rivoo.salon.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record BusinessHoursRequest(
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        boolean isOpen,
        LocalTime openTime,
        LocalTime closeTime,
        LocalTime breakStartTime,
        LocalTime breakEndTime
) {
}
