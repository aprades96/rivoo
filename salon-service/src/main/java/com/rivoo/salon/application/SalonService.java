package com.rivoo.salon.application;

import com.rivoo.common.util.ExternalIdGenerator;
import com.rivoo.salon.application.dto.BusinessHoursRequest;
import com.rivoo.salon.application.dto.BusinessHoursResponse;
import com.rivoo.salon.application.dto.RegisterSalonRequest;
import com.rivoo.salon.application.dto.RegisterSalonResponse;
import com.rivoo.salon.application.dto.SalonPublicResponse;
import com.rivoo.salon.application.dto.SalonResponse;
import com.rivoo.salon.application.dto.UpdateSalonRequest;
import com.rivoo.salon.domain.exception.SalonNotFoundException;
import com.rivoo.salon.domain.exception.SlugAlreadyExistsException;
import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonBusinessHours;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.model.SubscriptionPlan;
import com.rivoo.salon.domain.port.in.GetSalonUseCase;
import com.rivoo.salon.domain.port.in.ListSalonsUseCase;
import com.rivoo.salon.domain.port.in.ManageBusinessHoursUseCase;
import com.rivoo.salon.domain.port.in.ManageSalonStatusUseCase;
import com.rivoo.salon.domain.port.in.RegisterSalonUseCase;
import com.rivoo.salon.domain.port.in.UpdateSalonUseCase;
import com.rivoo.salon.domain.port.out.AuthServicePort;
import com.rivoo.salon.domain.port.out.BusinessHoursPersistencePort;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import com.rivoo.salon.infrastructure.mapper.SalonDtoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalonService implements RegisterSalonUseCase, GetSalonUseCase, UpdateSalonUseCase,
        ManageBusinessHoursUseCase, ManageSalonStatusUseCase, ListSalonsUseCase {

    private final SalonPersistencePort salonPersistencePort;
    private final BusinessHoursPersistencePort businessHoursPersistencePort;
    private final AuthServicePort authServicePort;
    private final SalonDtoMapper salonDtoMapper;

    // ── Registration (Onboarding Saga) ──────────────────────────────────

    @Override
    @Transactional
    public RegisterSalonResponse register(RegisterSalonRequest request) {
        log.info("Starting salon registration for '{}'", request.name());

        // Step 1: Generate IDs and slug
        String externalId = ExternalIdGenerator.generate("sal");
        String slug = generateUniqueSlug(request.name());

        // Step 2: Create salon entity (status=ONBOARDING, tenantId=externalId)
        Salon salon = Salon.builder()
                .externalId(externalId)
                .tenantId(externalId) // salon IS the tenant
                .name(request.name())
                .slug(slug)
                .email(request.email())
                .phone(request.phone())
                .description(request.description())
                .addressStreet(request.addressStreet())
                .addressCity(request.addressCity() != null ? request.addressCity() : "Barcelona")
                .addressPostalCode(request.addressPostalCode())
                .timezone("Europe/Madrid")
                .currency("EUR")
                .subscriptionPlan(SubscriptionPlan.FREE_TRIAL)
                .status(SalonStatus.ONBOARDING)
                .build();

        // Step 3: Persist salon
        Salon savedSalon = salonPersistencePort.save(salon);
        log.info("Salon persisted with externalId={}, slug={}", externalId, slug);

        // Step 4: Create default business hours
        createDefaultBusinessHours(savedSalon.getId());

        // Step 5: Register owner in Keycloak via auth-service
        String keycloakUserId;
        try {
            keycloakUserId = authServicePort.registerOwner(
                    externalId,
                    request.email(),
                    request.ownerPassword(),
                    request.ownerFirstName(),
                    request.ownerLastName(),
                    request.name(),
                    SubscriptionPlan.FREE_TRIAL.name());
            log.info("Owner registered in Keycloak: userId={}", keycloakUserId);
        } catch (Exception e) {
            log.error("Failed to register owner in Keycloak, compensating: deleting salon {}", externalId, e);
            salonPersistencePort.deleteById(savedSalon.getId());
            throw e;
        }

        // Step 6: Update salon with owner user ID
        try {
            savedSalon.setOwnerUserId(keycloakUserId);
            savedSalon.setStatus(SalonStatus.ACTIVE);
            savedSalon = salonPersistencePort.save(savedSalon);
            log.info("Salon activated: externalId={}, ownerUserId={}", externalId, keycloakUserId);
        } catch (Exception e) {
            log.error("Failed to activate salon, compensating: deleting Keycloak user {} and salon {}",
                    keycloakUserId, externalId, e);
            try {
                authServicePort.deleteUser(keycloakUserId);
            } catch (Exception compEx) {
                log.error("Compensation failed: could not delete Keycloak user {}", keycloakUserId, compEx);
            }
            salonPersistencePort.deleteById(savedSalon.getId());
            throw e;
        }

        // Step 7 (SKIP): billing-service — Fase 7
        log.info("Skipping billing-service integration (not implemented yet)");

        // Step 8 (SKIP): notification-service — Fase 8
        log.info("Skipping notification-service integration (not implemented yet)");

        return new RegisterSalonResponse(
                savedSalon.getExternalId(),
                savedSalon.getSlug(),
                savedSalon.getStatus().name());
    }

    // ── Get Salon ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public SalonResponse getByTenantId(String tenantId) {
        Salon salon = salonPersistencePort.findByTenantId(tenantId)
                .orElseThrow(() -> new SalonNotFoundException(tenantId));
        return salonDtoMapper.toResponse(salon);
    }

    @Override
    @Transactional(readOnly = true)
    public SalonResponse getBySlug(String slug) {
        Salon salon = salonPersistencePort.findBySlug(slug)
                .orElseThrow(() -> new SalonNotFoundException(slug));
        return salonDtoMapper.toResponse(salon);
    }

    @Override
    @Transactional(readOnly = true)
    public SalonPublicResponse getPublicBySlug(String slug) {
        Salon salon = salonPersistencePort.findBySlug(slug)
                .orElseThrow(() -> new SalonNotFoundException(slug));
        return salonDtoMapper.toPublicResponse(salon);
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

        Salon updated = salonPersistencePort.save(salon);
        log.info("Salon updated: tenantId={}", tenantId);
        return salonDtoMapper.toResponse(updated);
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
                .map(r -> SalonBusinessHours.builder()
                        .salonId(salon.getId())
                        .dayOfWeek(r.dayOfWeek())
                        .open(r.open())
                        .openTime(r.openTime())
                        .closeTime(r.closeTime())
                        .breakStartTime(r.breakStartTime())
                        .breakEndTime(r.breakEndTime())
                        .build())
                .toList();

        List<SalonBusinessHours> saved = businessHoursPersistencePort.saveAll(hours);
        log.info("Business hours updated for tenantId={}", tenantId);
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
        log.info("Salon status updated: tenantId={}, status={}", tenantId, status);
    }

    // ── List All (admin) ────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<SalonResponse> listAll(Pageable pageable) {
        return salonPersistencePort.findAll(pageable).map(salonDtoMapper::toResponse);
    }

    // ── Private Helpers ─────────────────────────────────────────────────

    private String generateUniqueSlug(String name) {
        String baseSlug = name.toLowerCase()
                .replaceAll("[^a-z0-9áéíóúñü]+", "-")
                .replaceAll("^-|-$", "");

        if (!salonPersistencePort.existsBySlug(baseSlug)) {
            return baseSlug;
        }

        // Append numeric suffix to deduplicate
        for (int i = 2; i <= 100; i++) {
            String candidate = baseSlug + "-" + i;
            if (!salonPersistencePort.existsBySlug(candidate)) {
                return candidate;
            }
        }

        throw new SlugAlreadyExistsException(baseSlug);
    }

    private void createDefaultBusinessHours(Long salonId) {
        List<SalonBusinessHours> defaults = new ArrayList<>();

        // Mon-Fri: 09:00-20:00
        for (int day = 1; day <= 5; day++) {
            defaults.add(SalonBusinessHours.builder()
                    .salonId(salonId)
                    .dayOfWeek(day)
                    .open(true)
                    .openTime(LocalTime.of(9, 0))
                    .closeTime(LocalTime.of(20, 0))
                    .build());
        }

        // Sat: 09:00-14:00
        defaults.add(SalonBusinessHours.builder()
                .salonId(salonId)
                .dayOfWeek(6)
                .open(true)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(14, 0))
                .build());

        // Sun: closed
        defaults.add(SalonBusinessHours.builder()
                .salonId(salonId)
                .dayOfWeek(7)
                .open(false)
                .build());

        businessHoursPersistencePort.saveAll(defaults);
        log.info("Default business hours created for salonId={}", salonId);
    }
}
