package com.rivoo.salon.application;

import com.rivoo.salon.application.dto.BusinessHoursRequest;
import com.rivoo.salon.application.dto.BusinessHoursResponse;
import com.rivoo.salon.application.dto.EmployeePublicResponse;
import com.rivoo.salon.application.dto.SalonPublicResponse;
import com.rivoo.salon.application.dto.SalonResponse;
import com.rivoo.salon.application.dto.ServicePublicResponse;
import com.rivoo.salon.application.dto.UpdateSalonRequest;
import com.rivoo.salon.domain.exception.SalonNotFoundException;
import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonBusinessHours;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.port.in.GetSalonUseCase;
import com.rivoo.salon.domain.port.in.ListSalonsUseCase;
import com.rivoo.salon.domain.port.in.ManageBusinessHoursUseCase;
import com.rivoo.salon.domain.port.in.ManageSalonStatusUseCase;
import com.rivoo.salon.domain.port.in.UpdateSalonUseCase;
import com.rivoo.salon.domain.port.out.BusinessHoursPersistencePort;
import com.rivoo.salon.domain.port.out.NotificationServicePort;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import com.rivoo.salon.domain.port.out.StaffServicePort;
import com.rivoo.salon.infrastructure.mapper.SalonDtoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalonService implements GetSalonUseCase, UpdateSalonUseCase,
        ManageBusinessHoursUseCase, ManageSalonStatusUseCase, ListSalonsUseCase {

    private final SalonPersistencePort salonPersistencePort;
    private final BusinessHoursPersistencePort businessHoursPersistencePort;
    private final StaffServicePort staffServicePort;
    private final SalonDtoMapper salonDtoMapper;
    private final SalonPublicSnapshotLoader salonPublicSnapshotLoader;
    private final NotificationServicePort notificationServicePort;

    // ── Get Salon ───────────────────────────────────────────────────────

    /**
     * Reads the caller's own salon and, the first time an authenticated caller arrives, publishes it.
     * <p>
     * <b>Why a read publishes anything.</b> Registration is anonymous, so the address on the form is
     * unproven and the salon is left {@code ONBOARDING} — invisible on every anonymous surface — on
     * purpose. What ends that wait is proof that the owner controls the address, and that proof is
     * already in the request: the owner is created in Keycloak with a pending {@code VERIFY_EMAIL}
     * required action, Keycloak refuses to complete a login while a required action is pending, so a
     * token for this tenant cannot exist unless the address was confirmed. Asking anyone is
     * redundant — the token IS the answer, and it arrives on its own.
     * <p>
     * <b>Not {@code @Transactional}.</b> Two reasons. The promotion is a single atomic conditional
     * statement that needs no transaction of its own to be correct, and the welcome mail is an HTTP
     * call that must not run while a JDBC connection is held — the rule
     * {@link SalonPublicSnapshotLoader} exists to enforce on the public read path.
     */
    @Override
    public SalonResponse getByTenantId(String tenantId, Boolean ownerEmailVerifiedClaim) {
        Salon salon = salonPersistencePort.findByTenantId(tenantId)
                .orElseThrow(() -> new SalonNotFoundException(tenantId));

        // The overwhelmingly common case - an already published salon whose owner is opening their
        // dashboard for the thousandth time - leaves here having issued ONE read and no write at
        // all. The write is reachable only from the single ONBOARDING row, once in its lifetime.
        if (salon.getStatus() != SalonStatus.ONBOARDING || Boolean.FALSE.equals(ownerEmailVerifiedClaim)) {
            return salonDtoMapper.toResponse(salon);
        }

        return salonDtoMapper.toResponse(publishOnOwnerArrival(tenantId, salon));
    }

    /**
     * Turns the ONBOARDING salon into a published one, exactly once however many callers arrive
     * together.
     * <p>
     * The database arbitrates, not this code: {@code activateIfOnboarding} is a conditional update
     * and only the caller whose statement changed a row is told so. Two dashboard loads racing (two
     * tabs, a refresh, a retried fetch) therefore produce one promotion and one welcome mail, and
     * the loser re-reads rather than assuming what the winner wrote — the status may also have moved
     * somewhere else entirely, since billing-service can suspend a tenant at any moment.
     */
    private Salon publishOnOwnerArrival(String tenantId, Salon salon) {
        if (salonPersistencePort.activateIfOnboarding(tenantId) == 0) {
            return salonPersistencePort.findByTenantId(tenantId)
                    .orElseThrow(() -> new SalonNotFoundException(tenantId));
        }

        salon.setStatus(SalonStatus.ACTIVE);
        log.atInfo().addKeyValue("externalId", salon.getExternalId())
                .log("Owner reached their dashboard with a verified address, salon is now publicly visible");

        // The welcome template reads "tu salon esta activo", so it belongs here and not at
        // registration: until this moment the statement was false. Fire-and-forget like every other
        // notification in this flow — a mail that does not go out must not undo a publication that
        // did, and must not turn the owner's dashboard into an error either.
        try {
            notificationServicePort.sendWelcomeEmail(salon.getTenantId(), salon.getEmail(), salon.getName());
        } catch (Exception e) {
            log.atWarn().setCause(e).addKeyValue("externalId", salon.getExternalId())
                    .log("Failed to send welcome email after publishing the salon, continuing");
        }
        return salon;
    }

    @Override
    @Transactional(readOnly = true)
    public SalonResponse getBySlug(String slug) {
        Salon salon = salonPersistencePort.findBySlug(slug)
                .orElseThrow(() -> new SalonNotFoundException(slug));
        return salonDtoMapper.toResponse(salon);
    }

    @Override
    public SalonPublicResponse getPublicBySlug(String slug) {
        // The DB read (salon + business hours) runs and commits inside
        // SalonPublicSnapshotLoader, in its own transaction. By the time we get
        // here the JDBC connection has already been released: the two staff-service
        // HTTP calls below never run while a connection is held, so a slow or
        // unresponsive staff-service cannot exhaust the HikariCP pool.
        SalonPublicSnapshot snapshot = salonPublicSnapshotLoader.loadActiveSalon(slug);
        Salon salon = snapshot.salon();

        List<BusinessHoursResponse> businessHours = snapshot.businessHours()
                .stream()
                .map(salonDtoMapper::toBusinessHoursResponse)
                .toList();

        // externalId == tenantId for a salon (the salon IS the tenant).
        // Optional.empty() means that particular staff-service call failed (see
        // StaffServicePort); a present Optional means it answered normally, even if
        // the wrapped list is empty (a salon that skipped the optional
        // employees/services onboarding step is not an unavailable catalogue). The two
        // calls fail independently: if only one fails, the other's real data still
        // reaches the response, and each flag below is derived ONLY from its own
        // call, never combined, so a partial failure stays partial for the two
        // reservation steps that each consume one list (public-service-step /
        // public-employee-step).
        Optional<List<StaffServicePort.ServicePublicInfo>> servicesResult =
                staffServicePort.getPublicServices(salon.getTenantId());
        Optional<List<StaffServicePort.EmployeePublicInfo>> employeesResult =
                staffServicePort.getPublicEmployees(salon.getTenantId());

        List<ServicePublicResponse> services = servicesResult.orElseGet(List::of)
                .stream()
                .map(salonDtoMapper::toServicePublicResponse)
                .toList();
        List<EmployeePublicResponse> employees = employeesResult.orElseGet(List::of)
                .stream()
                .map(salonDtoMapper::toEmployeePublicResponse)
                .toList();
        boolean servicesUnavailable = servicesResult.isEmpty();
        boolean employeesUnavailable = employeesResult.isEmpty();

        return salonDtoMapper.toPublicResponse(salon, businessHours, services, employees,
                servicesUnavailable, employeesUnavailable);
    }

    // ── Update Salon ────────────────────────────────────────────────────

    @Override
    @Transactional
    public SalonResponse update(String tenantId, UpdateSalonRequest request) {
        Salon salon = salonPersistencePort.findByTenantId(tenantId)
                .orElseThrow(() -> new SalonNotFoundException(tenantId));

        if (request.name() != null) salon.setName(request.name());
        if (request.email() != null) salon.setEmail(request.email());
        if (request.phone() != null) salon.setPhone(request.phone());
        if (request.description() != null) salon.setDescription(request.description());
        if (request.addressStreet() != null) salon.setAddressStreet(request.addressStreet());
        if (request.addressCity() != null) salon.setAddressCity(request.addressCity());
        if (request.addressPostalCode() != null) salon.setAddressPostalCode(request.addressPostalCode());
        if (request.timezone() != null) salon.setTimezone(request.timezone());
        if (request.currency() != null) salon.setCurrency(request.currency());
        if (request.logoUrl() != null) salon.setLogoUrl(request.logoUrl());
        if (request.primaryColor() != null) salon.setPrimaryColor(request.primaryColor());

        Salon updated = salonPersistencePort.save(salon);
        log.atInfo().log("Salon updated");
        return salonDtoMapper.toResponse(updated);
    }

    /**
     * Not {@code @Transactional}: {@code markOnboardingCompleted} is a single conditional
     * statement, already atomic on its own, and needs no transaction of its own to be correct.
     * Wrapping this method in one would not add isolation either - with the default
     * {@code REQUIRED} propagation, the repository method's own {@code @Transactional} would
     * simply join the outer transaction instead of committing independently - it would only hold
     * the JDBC connection open across the extra {@code findByTenantId} for no benefit. Same
     * reasoning as {@link #getByTenantId}, see its javadoc.
     */
    @Override
    public SalonResponse completeOnboarding(String tenantId) {
        salonPersistencePort.markOnboardingCompleted(tenantId);   // the count decides nothing here
        Salon salon = salonPersistencePort.findByTenantId(tenantId)
                .orElseThrow(() -> new SalonNotFoundException(tenantId));
        return salonDtoMapper.toResponse(salon);
    }

    // ── Business Hours ──────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<BusinessHoursResponse> getBusinessHours(String tenantId) {
        Salon salon = salonPersistencePort.findByTenantId(tenantId)
                .orElseThrow(() -> new SalonNotFoundException(tenantId));
        List<SalonBusinessHours> hours = businessHoursPersistencePort.findBySalonId(salon.getId());
        return hours.stream().map(salonDtoMapper::toBusinessHoursResponse).toList();
    }

    @Override
    @Transactional
    public List<BusinessHoursResponse> updateBusinessHours(String tenantId, List<BusinessHoursRequest> request) {
        Salon salon = salonPersistencePort.findByTenantId(tenantId)
                .orElseThrow(() -> new SalonNotFoundException(tenantId));

        businessHoursPersistencePort.deleteBySalonId(salon.getId());

        List<SalonBusinessHours> hours = request.stream()
                .map(r -> {
                    SalonBusinessHours bh = SalonBusinessHours.builder()
                            .salonId(salon.getId())
                            .dayOfWeek(r.dayOfWeek())
                            .open(r.isOpen())
                            .openTime(r.openTime())
                            .closeTime(r.closeTime())
                            .breakStartTime(r.breakStartTime())
                            .breakEndTime(r.breakEndTime())
                            .build();
                    bh.validate();
                    return bh;
                })
                .toList();

        List<SalonBusinessHours> saved = businessHoursPersistencePort.saveAll(hours);
        log.atInfo().log("Business hours updated");
        return saved.stream().map(salonDtoMapper::toBusinessHoursResponse).toList();
    }

    // ── Status Management (internal) ────────────────────────────────────

    @Override
    @Transactional
    public void updateStatus(String tenantId, SalonStatus status) {
        Salon salon = salonPersistencePort.findByTenantId(tenantId)
                .orElseThrow(() -> new SalonNotFoundException(tenantId));
        salon.setStatus(status);
        salonPersistencePort.save(salon);
        log.atInfo().addKeyValue("status", status).log("Salon status updated");
    }

    // ── List All (admin) ────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<SalonResponse> listAll(Pageable pageable) {
        return salonPersistencePort.findAll(pageable).map(salonDtoMapper::toResponse);
    }
}
