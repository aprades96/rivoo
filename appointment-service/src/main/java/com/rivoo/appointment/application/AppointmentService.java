package com.rivoo.appointment.application;

import com.rivoo.appointment.application.dto.AppointmentInternalResponse;
import com.rivoo.appointment.application.dto.AppointmentResponse;
import com.rivoo.appointment.application.dto.AppointmentStatsResponse;
import com.rivoo.appointment.application.dto.CancelAppointmentRequest;
import com.rivoo.appointment.application.dto.CreateAppointmentRequest;
import com.rivoo.appointment.domain.exception.AppointmentConflictException;
import com.rivoo.appointment.domain.exception.AppointmentLimitExceededException;
import com.rivoo.appointment.domain.exception.AppointmentNotFoundException;
import com.rivoo.appointment.domain.exception.InvalidStatusTransitionException;
import com.rivoo.appointment.domain.model.Appointment;
import com.rivoo.appointment.domain.model.AppointmentSource;
import com.rivoo.appointment.domain.model.AppointmentStatus;
import com.rivoo.appointment.domain.model.CancelledBy;
import com.rivoo.appointment.domain.port.in.AppointmentStatsUseCase;
import com.rivoo.appointment.domain.port.in.CancelAppointmentUseCase;
import com.rivoo.appointment.domain.port.in.CreateAppointmentUseCase;
import com.rivoo.appointment.domain.port.in.GetAppointmentUseCase;
import com.rivoo.appointment.domain.port.in.UpdateAppointmentStatusUseCase;
import com.rivoo.appointment.domain.port.out.AppointmentPersistencePort;
import com.rivoo.appointment.domain.port.out.BillingServicePort;
import com.rivoo.appointment.domain.port.out.ClientServicePort;
import com.rivoo.appointment.domain.port.out.NotificationServicePort;
import com.rivoo.appointment.domain.port.out.StaffServicePort;
import com.rivoo.appointment.infrastructure.mapper.AppointmentDtoMapper;
import com.rivoo.common.util.ExternalIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService implements CreateAppointmentUseCase, GetAppointmentUseCase,
        UpdateAppointmentStatusUseCase, CancelAppointmentUseCase, AppointmentStatsUseCase {

    private static final ZoneId SALON_TIMEZONE = ZoneId.of("Europe/Madrid");

    private final AppointmentPersistencePort appointmentPersistencePort;
    private final StaffServicePort staffServicePort;
    private final ClientServicePort clientServicePort;
    private final BillingServicePort billingServicePort;
    private final NotificationServicePort notificationServicePort;
    private final AppointmentDtoMapper mapper;

    @Override
    @Transactional
    public AppointmentResponse create(String tenantId, CreateAppointmentRequest request) {
        // 1. Check plan limits (bypass cache for write operations)
        checkPlanLimits(tenantId);

        // 2. Validate employee via staff-service
        StaffServicePort.StaffEmployeeInfo employee = staffServicePort.getEmployee(tenantId, request.employeeId());
        if (!employee.active()) {
            throw new com.rivoo.common.exception.BusinessValidationException("Employee is not active");
        }

        // 3. Validate service via staff-service
        StaffServicePort.StaffServiceInfo service = staffServicePort.getService(tenantId, request.serviceId());
        if (!service.active()) {
            throw new com.rivoo.common.exception.BusinessValidationException("Service is not active");
        }

        // 4. Validate client if clientId provided
        String clientName = request.clientName();
        String clientPhone = request.clientPhone();
        String clientEmail = request.clientEmail();
        String clientId = request.clientId();

        if (clientId != null && !clientId.isBlank()) {
            ClientServicePort.ClientInfo client = clientServicePort.getClient(tenantId, clientId);
            if (!client.active()) {
                throw new com.rivoo.common.exception.BusinessValidationException("Client is not active");
            }
            // Use client data as snapshot (override manual fields if client exists)
            clientName = client.fullName();
            clientPhone = client.phone();
            clientEmail = client.email();
        }

        // 5. Convert local time to UTC
        LocalDateTime localStartTime = request.startTime();
        ZonedDateTime zonedStart = localStartTime.atZone(SALON_TIMEZONE);
        Instant startTimeUtc = zonedStart.toInstant();
        Instant endTimeUtc = zonedStart.plusMinutes(service.durationMinutes()).toInstant();

        // 6. Check for overlapping appointments (with pessimistic lock)
        List<Appointment> overlapping = appointmentPersistencePort
                .findOverlappingForUpdate(tenantId, request.employeeId(), startTimeUtc, endTimeUtc);
        if (!overlapping.isEmpty()) {
            throw new AppointmentConflictException(employee.fullName(),
                    localStartTime + " - " + localStartTime.plusMinutes(service.durationMinutes()));
        }

        // 7. Build and save appointment with snapshot data
        AppointmentSource source = parseSource(request.source());

        Appointment appointment = Appointment.builder()
                .externalId(ExternalIdGenerator.generate("apt"))
                .tenantId(tenantId)
                .clientId(clientId)
                .clientName(clientName)
                .clientPhone(clientPhone)
                .clientEmail(clientEmail)
                .employeeId(request.employeeId())
                .employeeName(employee.fullName())
                .serviceId(request.serviceId())
                .serviceName(service.name())
                .servicePrice(service.price())
                .serviceDurationMinutes(service.durationMinutes())
                .startTime(startTimeUtc)
                .endTime(endTimeUtc)
                .status(AppointmentStatus.PENDING)
                .source(source)
                .notes(request.notes())
                .reminderSent(false)
                .build();

        Appointment saved = appointmentPersistencePort.save(appointment);

        // 8. Schedule notification (fire-and-forget)
        try {
            notificationServicePort.scheduleReminder(saved);
        } catch (Exception e) {
            log.atWarn().setCause(e).addKeyValue("appointmentId", saved.getExternalId())
                    .log("Failed to schedule notification — appointment created anyway");
        }

        log.atInfo()
                .addKeyValue("appointmentId", saved.getExternalId())
                .addKeyValue("employeeId", request.employeeId())
                .addKeyValue("startTime", startTimeUtc)
                .log("Appointment created");

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getByExternalId(String externalId) {
        Appointment appointment = findOrThrow(externalId);
        return mapper.toResponse(appointment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> list(String employeeId, Instant startDate, Instant endDate,
                                           String status, Pageable pageable) {
        AppointmentStatus statusEnum = status != null ? AppointmentStatus.valueOf(status) : null;
        return appointmentPersistencePort
                .findByFilters(employeeId, startDate, endDate, statusEnum, pageable)
                .map(mapper::toResponse);
    }

    @Override
    @Transactional
    public AppointmentResponse updateStatus(String externalId, String newStatus) {
        Appointment appointment = findOrThrow(externalId);
        AppointmentStatus targetStatus = AppointmentStatus.valueOf(newStatus);

        if (!appointment.canTransitionTo(targetStatus)) {
            throw new InvalidStatusTransitionException(
                    appointment.getStatus().name(), targetStatus.name());
        }

        appointment.setStatus(targetStatus);
        Appointment saved = appointmentPersistencePort.save(appointment);

        log.atInfo()
                .addKeyValue("appointmentId", externalId)
                .addKeyValue("newStatus", newStatus)
                .log("Appointment status updated");

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AppointmentResponse cancel(String externalId, CancelAppointmentRequest request) {
        Appointment appointment = findOrThrow(externalId);

        if (appointment.isTerminal()) {
            throw new InvalidStatusTransitionException(
                    appointment.getStatus().name(), AppointmentStatus.CANCELLED.name());
        }

        if (!appointment.canTransitionTo(AppointmentStatus.CANCELLED)) {
            throw new InvalidStatusTransitionException(
                    appointment.getStatus().name(), AppointmentStatus.CANCELLED.name());
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason(request.reason());
        if (request.cancelledBy() != null) {
            appointment.setCancelledBy(CancelledBy.valueOf(request.cancelledBy()));
        }

        Appointment saved = appointmentPersistencePort.save(appointment);

        // Cancel scheduled reminders (fire-and-forget)
        try {
            notificationServicePort.cancelReminders(externalId);
        } catch (Exception e) {
            log.atWarn().setCause(e).addKeyValue("appointmentId", externalId)
                    .log("Failed to cancel reminders — appointment cancelled anyway");
        }

        log.atInfo()
                .addKeyValue("appointmentId", externalId)
                .addKeyValue("cancelledBy", request.cancelledBy())
                .log("Appointment cancelled");

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentStatsResponse getStats(String tenantId) {
        YearMonth currentMonth = YearMonth.now(SALON_TIMEZONE);
        Instant monthStart = currentMonth.atDay(1).atStartOfDay(SALON_TIMEZONE).toInstant();
        Instant monthEnd = currentMonth.plusMonths(1).atDay(1).atStartOfDay(SALON_TIMEZONE).toInstant();

        long total = appointmentPersistencePort.countByTenantAndMonth(tenantId, monthStart, monthEnd);

        Map<String, Long> byStatus = new HashMap<>();
        for (AppointmentStatus status : AppointmentStatus.values()) {
            long count = appointmentPersistencePort.countByTenantAndStatus(tenantId, status);
            if (count > 0) {
                byStatus.put(status.name(), count);
            }
        }

        Map<String, Long> bySource = new HashMap<>();
        for (AppointmentSource source : AppointmentSource.values()) {
            long count = appointmentPersistencePort.countByTenantAndSource(tenantId, source.name());
            if (count > 0) {
                bySource.put(source.name(), count);
            }
        }

        return new AppointmentStatsResponse(total, byStatus, bySource);
    }

    private Appointment findOrThrow(String externalId) {
        return appointmentPersistencePort.findByExternalId(externalId)
                .orElseThrow(() -> new AppointmentNotFoundException(externalId));
    }

    private void checkPlanLimits(String tenantId) {
        int maxAppointments = billingServicePort.getMaxAppointmentsPerMonth(tenantId);
        if (maxAppointments >= 0) {
            YearMonth currentMonth = YearMonth.now(SALON_TIMEZONE);
            Instant monthStart = currentMonth.atDay(1).atStartOfDay(SALON_TIMEZONE).toInstant();
            Instant monthEnd = currentMonth.plusMonths(1).atDay(1).atStartOfDay(SALON_TIMEZONE).toInstant();
            long currentCount = appointmentPersistencePort.countByTenantAndMonth(tenantId, monthStart, monthEnd);
            if (currentCount >= maxAppointments) {
                throw new AppointmentLimitExceededException(maxAppointments);
            }
        }
    }

    private AppointmentSource parseSource(String source) {
        if (source == null || source.isBlank()) {
            return AppointmentSource.MANUAL;
        }
        try {
            return AppointmentSource.valueOf(source);
        } catch (IllegalArgumentException e) {
            return AppointmentSource.MANUAL;
        }
    }
}
