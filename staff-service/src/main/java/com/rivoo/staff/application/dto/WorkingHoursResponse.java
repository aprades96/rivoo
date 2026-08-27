package com.rivoo.staff.application.dto;

import java.time.LocalTime;

public record WorkingHoursResponse(
        int dayOfWeek,
        boolean isOpen,
        LocalTime openTime,
        LocalTime closeTime,
        LocalTime breakStartTime,
        LocalTime breakEndTime
) {
}
