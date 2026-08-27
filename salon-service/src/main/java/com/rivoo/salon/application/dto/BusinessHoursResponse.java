package com.rivoo.salon.application.dto;

import java.time.LocalTime;

public record BusinessHoursResponse(
        int dayOfWeek,
        boolean isOpen,
        LocalTime openTime,
        LocalTime closeTime,
        LocalTime breakStartTime,
        LocalTime breakEndTime
) {
}
