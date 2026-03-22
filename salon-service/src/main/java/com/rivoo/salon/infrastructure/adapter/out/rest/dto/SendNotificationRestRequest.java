package com.rivoo.salon.infrastructure.adapter.out.rest.dto;

import java.util.Map;

public record SendNotificationRestRequest(
        String tenantId,
        String recipientEmail,
        String type,
        String referenceType,
        String referenceId,
        Map<String, String> templateData
) {}
