package com.rivoo.admin.infrastructure.adapter.in.web;

import com.rivoo.admin.application.dto.AdminDashboardResponse;
import com.rivoo.admin.application.dto.AdminSalonResponse;
import com.rivoo.admin.application.dto.SuspendTenantRequest;
import com.rivoo.admin.infrastructure.adapter.out.rest.AppointmentAdminAdapter;
import com.rivoo.admin.infrastructure.adapter.out.rest.AuthAdminAdapter;
import com.rivoo.admin.infrastructure.adapter.out.rest.SalonAdminAdapter;
import com.rivoo.admin.infrastructure.adapter.out.rest.SalonStatusAdapter;
import com.rivoo.admin.infrastructure.adapter.out.rest.dto.AppointmentStatsDto;
import com.rivoo.admin.infrastructure.adapter.out.rest.dto.SalonAdminDto;
import com.rivoo.admin.infrastructure.adapter.out.rest.dto.TenantUsersDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SalonAdminAdapter salonAdminAdapter;
    private final AppointmentAdminAdapter appointmentAdminAdapter;
    private final AuthAdminAdapter authAdminAdapter;
    private final SalonStatusAdapter salonStatusAdapter;

    // ── Salons ───────────────────────────────────────────────────────────

    @GetMapping("/salons")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<AdminSalonResponse>> listSalons() {
        log.atInfo().log("GET /api/v1/admin/salons");

        List<SalonAdminDto> salons = salonAdminAdapter.listAllSalons();

        List<AdminSalonResponse> response = salons.stream()
                .map(dto -> new AdminSalonResponse(
                        dto.id(),
                        dto.name(),
                        dto.slug(),
                        dto.email(),
                        dto.status(),
                        dto.createdAt()
                ))
                .toList();

        log.atInfo().addKeyValue("count", response.size()).log("Returning salon list to admin");
        return ResponseEntity.ok(response);
    }

    // ── Appointments ─────────────────────────────────────────────────────

    @GetMapping("/appointments/stats")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<AppointmentStatsDto> getAppointmentStats(@RequestParam String tenantId) {
        log.atInfo().addKeyValue("tenantId", tenantId).log("GET /api/v1/admin/appointments/stats");

        AppointmentStatsDto stats = appointmentAdminAdapter.getAppointmentStats(tenantId);

        return ResponseEntity.ok(stats);
    }

    // ── Tenant management ────────────────────────────────────────────────

    /**
     * Suspend or reactivate a tenant.
     * SUSPENDED: disables all Keycloak users + sets salon status to SUSPENDED.
     * ACTIVE: re-enables all Keycloak users + sets salon status to ACTIVE.
     */
    @PutMapping("/tenants/{tenantId}/status")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> updateTenantStatus(
            @PathVariable String tenantId,
            @Valid @RequestBody SuspendTenantRequest request) {
        log.atInfo()
                .addKeyValue("tenantId", tenantId)
                .addKeyValue("status", request.status())
                .log("PUT /api/v1/admin/tenants/status");

        boolean enable = "ACTIVE".equals(request.status());

        // 1. Update Keycloak users (enable or disable)
        authAdminAdapter.setTenantEnabled(tenantId, enable);

        // 2. Update salon status in salon-service
        salonStatusAdapter.updateSalonStatus(tenantId, request.status());

        log.atInfo()
                .addKeyValue("tenantId", tenantId)
                .addKeyValue("status", request.status())
                .log("Tenant status updated successfully");

        return ResponseEntity.noContent().build();
    }

    // ── Users ────────────────────────────────────────────────────────────

    @GetMapping("/tenants/{tenantId}/users")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<TenantUsersDto>> getTenantUsers(@PathVariable String tenantId) {
        log.atInfo().addKeyValue("tenantId", tenantId).log("GET /api/v1/admin/tenants/users");

        List<TenantUsersDto> users = authAdminAdapter.getTenantUsers(tenantId);

        return ResponseEntity.ok(users);
    }

    // ── Dashboard ────────────────────────────────────────────────────────

    /**
     * Aggregated dashboard: total salons + appointment stats.
     * Uses a platform-wide view (no tenantId filter for salons).
     * For appointment stats a tenantId is required — use the dedicated endpoint instead.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        log.atInfo().log("GET /api/v1/admin/dashboard");

        List<SalonAdminDto> salons = salonAdminAdapter.listAllSalons();

        AdminDashboardResponse response = new AdminDashboardResponse(
                salons.size(),
                0L,                 // Platform-wide appointment stats require aggregation per tenant; use /appointments/stats?tenantId=X
                java.util.Map.of()
        );

        return ResponseEntity.ok(response);
    }
}
