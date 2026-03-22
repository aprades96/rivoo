package com.rivoo.appointment.infrastructure.adapter.out.rest.dto;

import java.util.Map;

public record ScheduleNotificationRestRequest(
        String tenantId,
        String recipientEmail,
        String type,
        String referenceType,
        String referenceId,
        String scheduledFor,
        Map<String, String> templateData
) {}
