package com.rivoo.salon.infrastructure.adapter.in.web;

import com.rivoo.common.security.TenantAwareJwtAuthenticationToken;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    /**
     * ANONYMOUS. Answers 202 rather than 201 because a salon is not always created: an address that
     * already has an account gets this exact status and this exact body with nothing created at
     * all, so claiming "Created" would be both a lie on one path and the difference an attacker
     * needs to enumerate accounts. The outcome reaches the user by email either way.
     */
    @PostMapping("/api/v1/salons")
    public ResponseEntity<RegisterSalonResponse> register(@Valid @RequestBody RegisterSalonRequest request) {
        log.atInfo().addKeyValue("salonName", request.name()).log("POST /api/v1/salons - Registering salon");
        RegisterSalonResponse response = registerSalonUseCase.register(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/api/v1/salons/public/{slug}")
    public ResponseEntity<SalonPublicResponse> getPublicBySlug(@PathVariable String slug) {
        log.atInfo().addKeyValue("slug", slug).log("GET /api/v1/salons/public/{slug}");
        SalonPublicResponse response = getSalonUseCase.getPublicBySlug(slug);
        return ResponseEntity.ok(response);
    }

    // ── Authenticated ───────────────────────────────────────────────────

    /**
     * The owner's dashboard read — and the only thing that ever publishes a salon.
     * <p>
     * {@code EMPLOYEE} is allowed here too and cannot short-circuit that: an employee account only
     * exists because a {@code SALON_OWNER} of the same tenant created it through staff-service,
     * which needs an owner token, which needs the owner to have completed {@code VERIFY_EMAIL} — by
     * which time this endpoint has already published the salon. Should that ever stop holding, an
     * employee arriving first would still be sound proof: their own token cannot exist without
     * someone having authenticated as the owner of this tenant to create them.
     */
    @GetMapping("/api/v1/salons/me")
    @PreAuthorize("hasAnyRole('SALON_OWNER', 'EMPLOYEE')")
    public ResponseEntity<SalonResponse> getMe() {
        String tenantId = TenantContext.getCurrentTenantId();
        log.atInfo().log("GET /api/v1/salons/me");
        SalonResponse response = getSalonUseCase.getByTenantId(tenantId, currentEmailVerifiedClaim());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/api/v1/salons/me")
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<SalonResponse> updateMe(@Valid @RequestBody UpdateSalonRequest request) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.atInfo().log("PUT /api/v1/salons/me");
        SalonResponse response = updateSalonUseCase.update(tenantId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/v1/salons/me/onboarding/complete")
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<SalonResponse> completeOnboarding() {
        String tenantId = TenantContext.getCurrentTenantId();
        log.atInfo().log("POST /api/v1/salons/me/onboarding/complete");
        return ResponseEntity.ok(updateSalonUseCase.completeOnboarding(tenantId));
    }

    @GetMapping("/api/v1/salons/me/business-hours")
    @PreAuthorize("hasAnyRole('SALON_OWNER', 'EMPLOYEE')")
    public ResponseEntity<List<BusinessHoursResponse>> getBusinessHours() {
        String tenantId = TenantContext.getCurrentTenantId();
        log.atInfo().log("GET /api/v1/salons/me/business-hours");
        List<BusinessHoursResponse> response = manageBusinessHoursUseCase.getBusinessHours(tenantId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/api/v1/salons/me/business-hours")
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<List<BusinessHoursResponse>> updateBusinessHours(
            @Valid @RequestBody List<BusinessHoursRequest> request) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.atInfo().log("PUT /api/v1/salons/me/business-hours");
        List<BusinessHoursResponse> response = manageBusinessHoursUseCase.updateBusinessHours(tenantId, request);
        return ResponseEntity.ok(response);
    }

    // ── Internal (PSK) ──────────────────────────────────────────────────

    @PutMapping("/api/internal/salons/{tenantId}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable String tenantId,
                                             @Valid @RequestBody UpdateStatusRequest request) {
        log.atInfo().addKeyValue("status", request.status()).log("PUT /api/internal/salons/status");
        SalonStatus status = SalonStatus.valueOf(request.status());
        manageSalonStatusUseCase.updateStatus(tenantId, status);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/internal/salons/by-slug/{slug}")
    public ResponseEntity<SalonResponse> getBySlug(@PathVariable String slug) {
        log.atInfo().addKeyValue("slug", slug).log("GET /api/internal/salons/by-slug");
        SalonResponse response = getSalonUseCase.getBySlug(slug);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/internal/admin/salons")
    public ResponseEntity<Page<SalonResponse>> listAll(Pageable pageable) {
        log.atInfo().log("GET /api/internal/admin/salons");
        Page<SalonResponse> response = listSalonsUseCase.listAll(pageable);
        return ResponseEntity.ok(response);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * The caller's {@code email_verified} claim, or {@code null} when there is nothing to read it
     * from.
     * <p>
     * Null covers two cases and both mean the same thing here — "the token asserts nothing about
     * it": a realm that does not map the claim, and an authentication that is not a Keycloak JWT at
     * all. Neither is a denial, and treating them as one would strand every owner on such a realm
     * with a salon nobody can find and no way to fix it themselves. The token's mere existence is
     * the real proof; the claim is belt and braces on top of it.
     */
    private static Boolean currentEmailVerifiedClaim() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof TenantAwareJwtAuthenticationToken token
                ? token.getEmailVerified()
                : null;
    }

}
