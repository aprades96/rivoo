package com.rivoo.appointment.infrastructure.adapter.out.persistence.entity;

import com.rivoo.appointment.domain.model.AppointmentSource;
import com.rivoo.appointment.domain.model.AppointmentStatus;
import com.rivoo.appointment.domain.model.CancelledBy;
import com.rivoo.common.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "appointments")
public class AppointmentJpaEntity extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true, length = 44)
    private String externalId;

    // Client snapshot
    @Column(name = "client_id", length = 44)
    private String clientId;

    @Column(name = "client_name", nullable = false, length = 200)
    private String clientName;

    @Column(name = "client_phone", length = 20)
    private String clientPhone;

    @Column(name = "client_email", length = 255)
    private String clientEmail;

    // Employee snapshot
    @Column(name = "employee_id", nullable = false, length = 44)
    private String employeeId;

    @Column(name = "employee_name", nullable = false, length = 200)
    private String employeeName;

    // Service snapshot
    @Column(name = "service_id", nullable = false, length = 44)
    private String serviceId;

    @Column(name = "service_name", nullable = false, length = 200)
    private String serviceName;

    @Column(name = "service_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal servicePrice;

    @Column(name = "service_duration_minutes", nullable = false)
    private int serviceDurationMinutes;

    // Scheduling
    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AppointmentStatus status;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by")
    private CancelledBy cancelledBy;

    // Metadata
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private AppointmentSource source;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "reminder_sent")
    private boolean reminderSent;

    // Timestamps
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
