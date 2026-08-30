package com.rivoo.appointment.domain.port.out;

import com.rivoo.appointment.domain.model.Appointment;
import com.rivoo.appointment.domain.model.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AppointmentPersistencePort {

    Appointment save(Appointment appointment);

    Optional<Appointment> findByExternalId(String externalId);

    Page<Appointment> findByFilters(String employeeId, Instant startDate, Instant endDate,
                                    AppointmentStatus status, Pageable pageable);

    List<Appointment> findOverlappingForUpdate(String tenantId, String employeeId,
                                               Instant startTime, Instant endTime);

    List<Appointment> findByEmployeeAndDateRange(String tenantId, String employeeId,
                                                 Instant startOfDay, Instant endOfDay);

    long countByTenantAndMonth(String tenantId, Instant monthStart, Instant monthEnd);

    long countByTenantAndStatus(String tenantId, AppointmentStatus status);

    long countByTenantAndSource(String tenantId, String source);

    List<Appointment> findByClientId(String clientId, String tenantId);

    /**
     * Paginated variant used by the client's appointment history (D38). Callers are
     * expected to pass a {@link Pageable} already carrying {@code startTime DESC} —
     * this port does not impose an order on its own.
     */
    Page<Appointment> findByClientId(String clientId, String tenantId, Pageable pageable);

    /**
     * Single aggregate query over the {@code COMPLETED} appointments of a client:
     * how many, how much they billed, and when the last one happened. Computed in
     * the database, never by loading the whole history into memory.
     */
    CompletedAppointmentsSummary getCompletedSummaryByClientId(String clientId, String tenantId);

    record CompletedAppointmentsSummary(long completedCount, BigDecimal billedAmount, Instant lastCompletedAt) {
    }
}
