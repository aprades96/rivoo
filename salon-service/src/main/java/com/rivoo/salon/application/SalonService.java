package com.rivoo.salon.application;

import com.rivoo.salon.application.dto.BusinessHoursRequest;
import com.rivoo.salon.application.dto.BusinessHoursResponse;
import com.rivoo.salon.application.dto.EmployeePublicDto;
import com.rivoo.salon.application.dto.SalonPublicResponse;
import com.rivoo.salon.application.dto.SalonResponse;
import com.rivoo.salon.application.dto.ServicePublicDto;
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
        List<ServicePublicDto> services = staffServicePort.getPublicServices(salon.getTenantId())
                .stream()
                .map(salonDtoMapper::toServicePublicDto)
                .toList();
        List<EmployeePublicDto> employees = staffServicePort.getPublicEmployees(salon.getTenantId())
                .stream()
                .map(salonDtoMapper::toEmployeePublicDto)
                .toList();

        return salonDtoMapper.toPublicResponse(salon, businessHours, services, employees);
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
