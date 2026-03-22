package com.rivoo.billing.application.dto;

public record PlanLimitsResponse(
        String planName,
        int maxEmployees,
        int maxAppointmentsPerMonth,
        boolean emailRemindersEnabled,
        boolean smsRemindersEnabled
) {
}
