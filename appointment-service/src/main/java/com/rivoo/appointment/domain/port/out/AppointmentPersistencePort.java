package com.rivoo.appointment.domain.port.out;

import com.rivoo.appointment.domain.model.Appointment;
import com.rivoo.appointment.domain.model.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
}
