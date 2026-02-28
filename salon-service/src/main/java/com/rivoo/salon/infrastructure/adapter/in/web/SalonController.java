package com.rivoo.salon.infrastructure.adapter.in.web;

import com.rivoo.common.tenant.TenantContext;
import com.rivoo.salon.application.dto.BusinessHoursRequest;
import com.rivoo.salon.application.dto.BusinessHoursResponse;
import com.rivoo.salon.application.dto.RegisterSalonRequest;
import com.rivoo.salon.application.dto.RegisterSalonResponse;
import com.rivoo.salon.application.dto.SalonPublicResponse;
import com.rivoo.salon.application.dto.SalonResponse;
import com.rivoo.salon.application.dto.UpdateSalonRequest;
import com.rivoo.salon.application.dto.UpdateStatusRequest;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.port.in.GetSalonUseCase;
import com.rivoo.salon.domain.port.in.ListSalonsUseCase;
import com.rivoo.salon.domain.port.in.ManageBusinessHoursUseCase;
import com.rivoo.salon.domain.port.in.ManageSalonStatusUseCase;
import com.rivoo.salon.domain.port.in.RegisterSalonUseCase;
import com.rivoo.salon.domain.port.in.UpdateSalonUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SalonController {

    private final RegisterSalonUseCase registerSalonUseCase;
    private final GetSalonUseCase getSalonUseCase;
    private final UpdateSalonUseCase updateSalonUseCase;
    private final ManageBusinessHoursUseCase manageBusinessHoursUseCase;
    private final ManageSalonStatusUseCase manageSalonStatusUseCase;
    private final ListSalonsUseCase listSalonsUseCase;

    // ── Public ──────────────────────────────────────────────────────────

    @PostMapping("/api/v1/salons")
    public ResponseEntity<RegisterSalonResponse> register(@Valid @RequestBody RegisterSalonRequest request) {
        log.info("POST /api/v1/salons - Registering salon '{}'", request.name());
        RegisterSalonResponse response = registerSalonUseCase.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/salons/public/{slug}")
    public ResponseEntity<SalonPublicResponse> getPublicBySlug(@PathVariable String slug) {
        log.info("GET /api/v1/salons/public/{}", slug);
        SalonPublicResponse response = getSalonUseCase.getPublicBySlug(slug);
        return ResponseEntity.ok(response);
    }

    // ── Authenticated ───────────────────────────────────────────────────

    @GetMapping("/api/v1/salons/me")
    @PreAuthorize("hasAnyRole('SALON_OWNER', 'EMPLOYEE')")
    public ResponseEntity<SalonResponse> getMe() {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("GET /api/v1/salons/me - tenantId={}", tenantId);
        SalonResponse response = getSalonUseCase.getByTenantId(tenantId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/api/v1/salons/me")
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<SalonResponse> updateMe(@Valid @RequestBody UpdateSalonRequest request) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("PUT /api/v1/salons/me - tenantId={}", tenantId);
        SalonResponse response = updateSalonUseCase.update(tenantId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/salons/me/business-hours")
    @PreAuthorize("hasAnyRole('SALON_OWNER', 'EMPLOYEE')")
    public ResponseEntity<List<BusinessHoursResponse>> getBusinessHours() {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("GET /api/v1/salons/me/business-hours - tenantId={}", tenantId);
        List<BusinessHoursResponse> response = manageBusinessHoursUseCase.getBusinessHours(tenantId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/api/v1/salons/me/business-hours")
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<List<BusinessHoursResponse>> updateBusinessHours(
            @Valid @RequestBody List<BusinessHoursRequest> request) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("PUT /api/v1/salons/me/business-hours - tenantId={}", tenantId);
        List<BusinessHoursResponse> response = manageBusinessHoursUseCase.updateBusinessHours(tenantId, request);
        return ResponseEntity.ok(response);
    }

    // ── Internal (PSK) ──────────────────────────────────────────────────

    @PutMapping("/api/internal/salons/{tenantId}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable String tenantId,
                                             @Valid @RequestBody UpdateStatusRequest request) {
        log.info("PUT /api/internal/salons/{}/status - status={}", tenantId, request.status());
        SalonStatus status = SalonStatus.valueOf(request.status());
        manageSalonStatusUseCase.updateStatus(tenantId, status);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/internal/salons/by-slug/{slug}")
    public ResponseEntity<SalonResponse> getBySlug(@PathVariable String slug) {
        log.info("GET /api/internal/salons/by-slug/{}", slug);
        SalonResponse response = getSalonUseCase.getBySlug(slug);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/internal/admin/salons")
    public ResponseEntity<Page<SalonResponse>> listAll(Pageable pageable) {
        log.info("GET /api/internal/admin/salons");
        Page<SalonResponse> response = listSalonsUseCase.listAll(pageable);
        return ResponseEntity.ok(response);
    }

}
