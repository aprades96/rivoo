package com.rivoo.appointment.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.appointment.domain.model.Appointment;
import com.rivoo.appointment.domain.model.AppointmentStatus;
import com.rivoo.appointment.domain.port.out.AppointmentPersistencePort;
import com.rivoo.appointment.infrastructure.adapter.out.persistence.repository.AppointmentAggregateProjection;
import com.rivoo.appointment.infrastructure.adapter.out.persistence.repository.AppointmentJpaRepository;
import com.rivoo.appointment.infrastructure.mapper.AppointmentPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AppointmentPersistenceAdapter implements AppointmentPersistencePort {

    private static final List<AppointmentStatus> EXCLUDED_STATUSES = List.of(
            AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW);

    private final AppointmentJpaRepository repository;
    private final AppointmentPersistenceMapper mapper;

    @Override
    public Appointment save(Appointment appointment) {
        var entity = mapper.toJpaEntity(appointment);
        var saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Appointment> findByExternalId(String externalId) {
        return repository.findByExternalId(externalId).map(mapper::toDomain);
    }

    @Override
    public Page<Appointment> findByFilters(String employeeId, Instant startDate, Instant endDate,
                                            AppointmentStatus status, Pageable pageable) {
        return repository.findByFilters(employeeId, startDate, endDate, status, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<Appointment> findOverlappingForUpdate(String tenantId, String employeeId,
                                                       Instant startTime, Instant endTime) {
        return repository.findOverlappingForUpdate(tenantId, employeeId, startTime, endTime, EXCLUDED_STATUSES)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Appointment> findByEmployeeAndDateRange(String tenantId, String employeeId,
                                                         Instant startOfDay, Instant endOfDay) {
        return repository.findByEmployeeAndDateRange(tenantId, employeeId, startOfDay, endOfDay, EXCLUDED_STATUSES)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long countByTenantAndMonth(String tenantId, Instant monthStart, Instant monthEnd) {
        return repository.countByTenantAndMonth(tenantId, monthStart, monthEnd, EXCLUDED_STATUSES);
    }

    @Override
    public long countByTenantAndStatus(String tenantId, AppointmentStatus status) {
        // Not directly needed — stats use grouped queries
        return 0;
    }

    @Override
    public long countByTenantAndSource(String tenantId, String source) {
        // Not directly needed — stats use grouped queries
        return 0;
    }

    @Override
    public List<Appointment> findByClientId(String clientId, String tenantId) {
        return repository.findByClientIdAndTenantId(clientId, tenantId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Page<Appointment> findByClientId(String clientId, String tenantId, Pageable pageable) {
        return repository.findByClientIdAndTenantId(clientId, tenantId, pageable).map(mapper::toDomain);
    }

    @Override
    public CompletedAppointmentsSummary getCompletedSummaryByClientId(String clientId, String tenantId) {
        AppointmentAggregateProjection aggregate =
                repository.aggregateByClientAndStatus(clientId, tenantId, AppointmentStatus.COMPLETED);
        long completedCount = aggregate.completedCount() != null ? aggregate.completedCount() : 0L;
        BigDecimal billedAmount = aggregate.billedAmount() != null ? aggregate.billedAmount() : BigDecimal.ZERO;
        Instant lastCompletedAt = aggregate.lastCompletedAt();
        return new CompletedAppointmentsSummary(completedCount, billedAmount, lastCompletedAt);
    }
}
