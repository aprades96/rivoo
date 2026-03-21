package com.rivoo.appointment.domain.port.in;

import com.rivoo.appointment.application.dto.AvailabilityResponse;

import java.time.LocalDate;

public interface CheckAvailabilityUseCase {
    AvailabilityResponse getAvailableSlots(String tenantId, String employeeId, LocalDate date, String serviceId);
}
