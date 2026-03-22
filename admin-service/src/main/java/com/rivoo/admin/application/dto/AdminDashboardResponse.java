package com.rivoo.admin.application.dto;

import java.util.Map;

public record AdminDashboardResponse(
        long totalSalons,
        long totalAppointmentsThisMonth,
        Map<String, Long> appointmentsByStatus
) {
}
