package com.rivoo.auth.domain.model;

import java.time.Instant;

public class OnboardingEvent {

    private Long id;
    private String tenantId;
    private String keycloakUserId;
    private String email;
    private EventType eventType;
    private String details;
    private Instant createdAt;

    public OnboardingEvent() {
    }

    public OnboardingEvent(String tenantId, String keycloakUserId, String email,
                           EventType eventType, String details) {
        this.tenantId = tenantId;
        this.keycloakUserId = keycloakUserId;
        this.email = email;
        this.eventType = eventType;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getKeycloakUserId() {
        return keycloakUserId;
    }

    public void setKeycloakUserId(String keycloakUserId) {
        this.keycloakUserId = keycloakUserId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
