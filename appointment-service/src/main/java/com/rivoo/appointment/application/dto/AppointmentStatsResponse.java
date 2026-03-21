package com.rivoo.appointment.application.dto;

import java.util.Map;

public record AppointmentStatsResponse(
        long totalThisMonth,
        Map<String, Long> byStatus,
        Map<String, Long> bySource
) {
}
