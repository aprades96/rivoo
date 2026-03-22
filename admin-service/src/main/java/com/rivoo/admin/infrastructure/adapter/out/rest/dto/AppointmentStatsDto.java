package com.rivoo.admin.infrastructure.adapter.out.rest.dto;

import java.util.Map;

public record AppointmentStatsDto(
        long totalThisMonth,
        Map<String, Long> byStatus,
        Map<String, Long> bySource
) {
}
