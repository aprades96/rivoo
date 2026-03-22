package com.rivoo.appointment.infrastructure.adapter.in.web;

import com.rivoo.appointment.application.dto.AppointmentResponse;
import com.rivoo.appointment.application.dto.AvailabilityResponse;
import com.rivoo.appointment.application.dto.CancelAppointmentRequest;
import com.rivoo.appointment.application.dto.CreateAppointmentRequest;
import com.rivoo.appointment.application.dto.PublicBookingRequest;
import com.rivoo.appointment.application.dto.PublicBookingResponse;
import com.rivoo.appointment.application.dto.UpdateStatusRequest;
import com.rivoo.appointment.domain.port.in.CancelAppointmentUseCase;
import com.rivoo.appointment.domain.port.in.CheckAvailabilityUseCase;
import com.rivoo.appointment.domain.port.in.CreateAppointmentUseCase;
import com.rivoo.appointment.domain.port.in.GetAppointmentUseCase;
import com.rivoo.appointment.domain.port.in.PublicBookingUseCase;
import com.rivoo.appointment.domain.port.in.UpdateAppointmentStatusUseCase;
import com.rivoo.common.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final CreateAppointmentUseCase createAppointmentUseCase;
    private final GetAppointmentUseCase getAppointmentUseCase;
    private final UpdateAppointmentStatusUseCase updateAppointmentStatusUseCase;
    private final CancelAppointmentUseCase cancelAppointmentUseCase;
    private final CheckAvailabilityUseCase checkAvailabilityUseCase;
    private final PublicBookingUseCase publicBookingUseCase;

    @PostMapping("/book")
    public ResponseEntity<PublicBookingResponse> publicBook(@Valid @RequestBody PublicBookingRequest request) {
        log.atInfo().addKeyValue("salonSlug", request.salonSlug()).log("POST /api/v1/appointments/book");
        PublicBookingResponse response = publicBookingUseCase.book(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SALON_OWNER', 'EMPLOYEE')")
    public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody CreateAppointmentRequest request) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.atInfo().log("POST /api/v1/appointments");
        AppointmentResponse response = createAppointmentUseCase.create(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SALON_OWNER', 'EMPLOYEE')")
    public ResponseEntity<Page<AppointmentResponse>> list(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        log.atInfo().log("GET /api/v1/appointments");
        Page<AppointmentResponse> response = getAppointmentUseCase.list(employeeId, startDate, endDate, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SALON_OWNER', 'EMPLOYEE')")
    public ResponseEntity<AppointmentResponse> getById(@PathVariable String id) {
        log.atInfo().addKeyValue("appointmentId", id).log("GET /api/v1/appointments/{id}");
        AppointmentResponse response = getAppointmentUseCase.getByExternalId(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SALON_OWNER', 'EMPLOYEE')")
    public ResponseEntity<AppointmentResponse> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateStatusRequest request) {
        log.atInfo().addKeyValue("appointmentId", id).addKeyValue("newStatus", request.status()).log("PUT /api/v1/appointments/{id}/status");
        AppointmentResponse response = updateAppointmentStatusUseCase.updateStatus(id, request.status());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SALON_OWNER', 'EMPLOYEE')")
    public ResponseEntity<AppointmentResponse> cancel(
            @PathVariable String id,
            @Valid @RequestBody CancelAppointmentRequest request) {
        log.atInfo().addKeyValue("appointmentId", id).log("PUT /api/v1/appointments/{id}/cancel");
        AppointmentResponse response = cancelAppointmentUseCase.cancel(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/availability")
    @PreAuthorize("hasAnyRole('SALON_OWNER', 'EMPLOYEE')")
    public ResponseEntity<AvailabilityResponse> checkAvailability(
            @RequestParam String employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String serviceId) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.atInfo().addKeyValue("employeeId", employeeId).addKeyValue("date", date).log("GET /api/v1/appointments/availability");
        AvailabilityResponse response = checkAvailabilityUseCase.getAvailableSlots(tenantId, employeeId, date, serviceId);
        return ResponseEntity.ok(response);
    }
}
