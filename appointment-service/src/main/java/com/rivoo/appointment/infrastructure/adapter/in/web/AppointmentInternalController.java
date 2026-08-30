package com.rivoo.appointment.infrastructure.adapter.in.web;

import com.rivoo.appointment.application.dto.AppointmentHistoryResponse;
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
        log.atInfo().log("GET /api/internal/admin/appointments/stats");
        AppointmentStatsResponse response = appointmentStatsUseCase.getStats(tenantId);
        return ResponseEntity.ok(response);
    }

    // Unpaged, unordered — kept exactly as-is for the GDPR export flow (ClientService.export),
    // which needs the full history and swallows failures on purpose. Do NOT add page/size
    // here: this endpoint stays selected only when the request has neither.
    @GetMapping("/by-client/{clientId}")
    public ResponseEntity<List<AppointmentInternalResponse>> getByClient(
            @PathVariable String clientId,
            @RequestParam String tenantId) {
        log.atInfo().addKeyValue("clientId", clientId).log("GET /api/internal/admin/appointments/by-client");
        List<AppointmentInternalResponse> response = getAppointmentUseCase.getByClientId(clientId, tenantId);
        return ResponseEntity.ok(response);
    }

    // Same path, discriminated by the presence of page/size (Spring dispatches on "params"):
    // the paginated history for the client screen (D38). Ordered startTime DESC, with a
    // billing summary computed from aggregate queries.
    @GetMapping(path = "/by-client/{clientId}", params = {"page", "size"})
    public ResponseEntity<AppointmentHistoryResponse> getByClientPaged(
            @PathVariable String clientId,
            @RequestParam String tenantId,
            @RequestParam int page,
            @RequestParam int size) {
        log.atInfo().addKeyValue("clientId", clientId).addKeyValue("page", page).addKeyValue("size", size)
                .log("GET /api/internal/admin/appointments/by-client (paginated)");
        AppointmentHistoryResponse response = getAppointmentUseCase.getHistoryByClientId(clientId, tenantId, page, size);
        return ResponseEntity.ok(response);
    }
}
