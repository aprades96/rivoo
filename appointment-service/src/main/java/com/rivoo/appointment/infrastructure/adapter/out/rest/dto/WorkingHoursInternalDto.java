package com.rivoo.appointment.infrastructure.adapter.out.rest.dto;

import java.time.LocalTime;

public record WorkingHoursInternalDto(
        int dayOfWeek,
        boolean open,
        LocalTime openTime,
        LocalTime closeTime,
        LocalTime breakStartTime,
        LocalTime breakEndTime
) {
}
