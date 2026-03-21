package com.rivoo.appointment.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {

    private Long id;
    private String externalId;
    private String tenantId;

    // Client snapshot (immutable after booking)
    private String clientId;
    private String clientName;
    private String clientPhone;
    private String clientEmail;

    // Employee snapshot
    private String employeeId;
    private String employeeName;

    // Service snapshot
    private String serviceId;
    private String serviceName;
    private BigDecimal servicePrice;
    private int serviceDurationMinutes;

    // Scheduling
    private Instant startTime;
    private Instant endTime;

    // Status
    private AppointmentStatus status;
    private String cancellationReason;
    private CancelledBy cancelledBy;

    // Metadata
    private AppointmentSource source;
    private String notes;
    private boolean reminderSent;

    // Timestamps
    private Instant createdAt;
    private Instant updatedAt;

    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }

    public boolean canTransitionTo(AppointmentStatus target) {
        return status != null && status.canTransitionTo(target);
    }
}
