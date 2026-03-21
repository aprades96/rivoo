package com.rivoo.appointment.domain.port.in;

import com.rivoo.appointment.application.dto.AppointmentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface GetAppointmentUseCase {
    AppointmentResponse getByExternalId(String externalId);
    Page<AppointmentResponse> list(String employeeId, Instant startDate, Instant endDate, String status, Pageable pageable);
}
