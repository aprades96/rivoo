package com.rivoo.auth.domain.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
public class OnboardingEvent {

    private Long id;
    private String tenantId;
    private String keycloakUserId;
    private String email;
    private EventType eventType;
    private String details;
    private Instant createdAt;

    public OnboardingEvent(String tenantId, String keycloakUserId, String email,
                           EventType eventType, String details) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(keycloakUserId, "keycloakUserId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        this.tenantId = tenantId;
        this.keycloakUserId = keycloakUserId;
        this.email = email;
        this.eventType = eventType;
        this.details = details;
    }
}
