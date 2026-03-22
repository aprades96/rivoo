package com.rivoo.staff.infrastructure.adapter.out.rest.dto;

public record PlanLimitsDto(
        String planName,
        int maxEmployees,
        int maxAppointmentsPerMonth,
        boolean emailRemindersEnabled,
        boolean smsRemindersEnabled
) {
}
