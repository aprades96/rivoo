package com.rivoo.appointment.application.dto;

import java.time.LocalTime;

public record EmployeeWorkingHoursDto(
        int dayOfWeek,
        boolean open,
        LocalTime openTime,
        LocalTime closeTime,
        LocalTime breakStartTime,
        LocalTime breakEndTime
) {
}
