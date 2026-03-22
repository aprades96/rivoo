package com.rivoo.notification.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record SendNotificationRequest(
        @NotBlank String tenantId,
        @NotBlank String recipientEmail,
        @NotBlank String type,
        String referenceType,
        String referenceId,
        Map<String, String> templateData
) {}
