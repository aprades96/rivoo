package com.rivoo.notification.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    private Long id;
    private String externalId;
    private String tenantId;
    private String recipientEmail;
    private NotificationChannel channel;
    private NotificationType type;
    private String referenceType;
    private String referenceId;
    private String subject;
    private String body;
    private NotificationStatus status;
    private Instant scheduledFor;
    private Instant sentAt;
    private int retryCount;
    private Instant createdAt;
    private Instant updatedAt;
}
