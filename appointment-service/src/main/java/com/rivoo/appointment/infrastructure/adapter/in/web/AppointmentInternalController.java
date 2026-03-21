package com.rivoo.appointment.infrastructure.adapter.in.web;

import com.rivoo.appointment.application.dto.AppointmentStatsResponse;
import com.rivoo.appointment.domain.port.in.AppointmentStatsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/internal/admin/appointments")
@RequiredArgsConstructor
public class AppointmentInternalController {

    private final AppointmentStatsUseCase appointmentStatsUseCase;

    @GetMapping("/stats")
    public ResponseEntity<AppointmentStatsResponse> getStats(@RequestParam String tenantId) {
        log.atInfo().addKeyValue("tenantId", tenantId).log("GET /api/internal/admin/appointments/stats");
        AppointmentStatsResponse response = appointmentStatsUseCase.getStats(tenantId);
        return ResponseEntity.ok(response);
    }
}
