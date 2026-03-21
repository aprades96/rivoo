package com.rivoo.appointment.application.dto;

import java.time.LocalTime;

public record AvailableSlot(
        LocalTime startTime,
        LocalTime endTime
) {
}
