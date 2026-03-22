package com.rivoo.notification.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record ScheduleNotificationRequest(
        @NotBlank String tenantId,
        @NotBlank String recipientEmail,
        @NotBlank String type,
        String referenceType,
        String referenceId,
        @NotNull Instant scheduledFor,
        Map<String, String> templateData
) {}
