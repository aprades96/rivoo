package com.rivoo.notification.application.dto;

import java.time.Instant;

public record NotificationResponse(
        String id,
        String type,
        String status,
        Instant scheduledFor,
        Instant sentAt
) {}
