package com.rivoo.appointment.infrastructure.adapter.in.web;

import com.rivoo.appointment.application.dto.AppointmentInternalResponse;
import com.rivoo.appointment.application.dto.AppointmentStatsResponse;
import com.rivoo.appointment.domain.port.in.AppointmentStatsUseCase;
import com.rivoo.appointment.domain.port.in.GetAppointmentUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/internal/admin/appointments")
@RequiredArgsConstructor
public class AppointmentInternalController {

    private final AppointmentStatsUseCase appointmentStatsUseCase;
    private final GetAppointmentUseCase getAppointmentUseCase;

    @GetMapping("/stats")
    public ResponseEntity<AppointmentStatsResponse> getStats(@RequestParam String tenantId) {
        log.atInfo().addKeyValue("tenantId", tenantId).log("GET /api/internal/admin/appointments/stats");
        AppointmentStatsResponse response = appointmentStatsUseCase.getStats(tenantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-client/{clientId}")
    public ResponseEntity<List<AppointmentInternalResponse>> getByClient(
            @PathVariable String clientId,
            @RequestParam String tenantId) {
        log.atInfo().addKeyValue("clientId", clientId).log("GET /api/internal/admin/appointments/by-client");
        List<AppointmentInternalResponse> response = getAppointmentUseCase.getByClientId(clientId, tenantId);
        return ResponseEntity.ok(response);
    }
}
