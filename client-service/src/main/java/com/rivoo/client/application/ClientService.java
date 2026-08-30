package com.rivoo.client.application;

import com.rivoo.client.application.dto.ClientAppointmentDto;
import com.rivoo.client.application.dto.ClientExportResponse;
import com.rivoo.client.application.dto.ClientInternalResponse;
import com.rivoo.client.application.dto.ClientResponse;
import com.rivoo.client.application.dto.CreateClientRequest;
import com.rivoo.client.application.dto.FindOrCreateClientRequest;
import com.rivoo.client.application.dto.UpdateClientRequest;
import com.rivoo.client.domain.exception.ClientAlreadyAnonymizedException;
import com.rivoo.client.domain.exception.ClientNotFoundException;
import com.rivoo.client.domain.exception.DuplicateClientEmailException;
import com.rivoo.client.domain.model.Client;
import com.rivoo.client.domain.model.ClientSource;
import com.rivoo.client.domain.model.Gender;
import com.rivoo.client.domain.port.in.AnonymizeClientUseCase;
import com.rivoo.client.domain.port.in.CreateClientUseCase;
import com.rivoo.client.domain.port.in.ExportClientDataUseCase;
import com.rivoo.client.domain.port.in.GetClientUseCase;
import com.rivoo.client.domain.port.in.InternalClientUseCase;
import com.rivoo.client.domain.port.in.UpdateClientUseCase;
import com.rivoo.client.domain.port.out.AppointmentServicePort;
import com.rivoo.client.domain.port.out.ClientPersistencePort;
import com.rivoo.client.infrastructure.mapper.ClientDtoMapper;
import com.rivoo.common.util.ExternalIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService implements CreateClientUseCase, GetClientUseCase,
        UpdateClientUseCase, AnonymizeClientUseCase, ExportClientDataUseCase, InternalClientUseCase {

    private final ClientPersistencePort clientPersistencePort;
    private final AppointmentServicePort appointmentServicePort;
    private final ClientDtoMapper mapper;

    // ── Create Client ───────────────────────────────────────────────────

    @Override
    @Transactional
    public ClientResponse create(String tenantId, CreateClientRequest request) {
        if (request.email() != null && clientPersistencePort.existsByTenantIdAndEmail(tenantId, request.email())) {
            throw new DuplicateClientEmailException(request.email());
        }

        Client client = Client.builder()
                .externalId(ExternalIdGenerator.generate("cli"))
                .tenantId(tenantId)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phone(request.phone())
                .gender(request.gender() != null ? Gender.valueOf(request.gender()) : null)
                .source(request.source() != null ? ClientSource.valueOf(request.source()) : ClientSource.WALK_IN)
                .notes(request.notes())
                .totalVisits(0)
                .gdprConsentAt(Instant.now())
                .active(true)
                .build();

        Client saved = clientPersistencePort.save(client);
        log.atInfo().addKeyValue("externalId", saved.getExternalId()).log("Client created");
        return mapper.toResponse(saved);
    }

    // ── Get Client ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ClientResponse getByExternalId(String externalId) {
        Client client = clientPersistencePort.findByExternalId(externalId)
                .orElseThrow(() -> new ClientNotFoundException(externalId));
        return mapper.toResponse(client);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClientResponse> list(String search, Pageable pageable) {
        // Order (lastVisitAt DESC, createdAt DESC) is fixed in the repository query,
        // not delegated to the caller's sort — strip it here so every consumer
        // (new-appointment assistant, /clients screen) sees the same ordering.
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return clientPersistencePort.findAll(normalizeSearch(search), unsorted).map(mapper::toResponse);
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String trimmed = search.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ── Update Client ───────────────────────────────────────────────────

    @Override
    @Transactional
    public ClientResponse update(String externalId, UpdateClientRequest request) {
        Client client = clientPersistencePort.findByExternalId(externalId)
                .orElseThrow(() -> new ClientNotFoundException(externalId));

        if (client.isAnonymized()) {
            throw new ClientAlreadyAnonymizedException(externalId);
        }

        if (request.firstName() != null) client.setFirstName(request.firstName());
        if (request.lastName() != null) client.setLastName(request.lastName());
        if (request.email() != null) client.setEmail(request.email());
        if (request.phone() != null) client.setPhone(request.phone());
        if (request.gender() != null) client.setGender(Gender.valueOf(request.gender()));
        if (request.source() != null) client.setSource(ClientSource.valueOf(request.source()));
        if (request.notes() != null) client.setNotes(request.notes());

        Client updated = clientPersistencePort.save(client);
        log.atInfo().addKeyValue("externalId", externalId).log("Client updated");
        return mapper.toResponse(updated);
    }

    // ── Anonymize (GDPR) ───────────────────────────────────────────────

    @Override
    @Transactional
    public void anonymize(String externalId) {
        Client client = clientPersistencePort.findByExternalId(externalId)
                .orElseThrow(() -> new ClientNotFoundException(externalId));

        if (client.isAnonymized()) {
            throw new ClientAlreadyAnonymizedException(externalId);
        }

        client.anonymize();
        clientPersistencePort.save(client);
        log.atInfo().addKeyValue("externalId", externalId).log("Client anonymized (GDPR)");
    }

    // ── Export (GDPR) ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ClientExportResponse export(String externalId) {
        Client client = clientPersistencePort.findByExternalId(externalId)
                .orElseThrow(() -> new ClientNotFoundException(externalId));

        List<ClientAppointmentDto> appointments = List.of();
        try {
            appointments = appointmentServicePort.getClientAppointments(externalId, client.getTenantId());
        } catch (Exception e) {
            log.atWarn().setCause(e).addKeyValue("clientId", externalId)
                    .log("Failed to fetch appointments for GDPR export");
        }

        return mapper.toExportResponse(client, appointments);
    }

    // ── Internal ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ClientInternalResponse getByExternalIdAndTenant(String externalId, String tenantId) {
        Client client = clientPersistencePort.findByExternalIdAndTenantId(externalId, tenantId)
                .orElseThrow(() -> new ClientNotFoundException(externalId));
        return mapper.toInternalResponse(client);
    }

    @Override
    @Transactional
    public ClientInternalResponse findOrCreate(String tenantId, FindOrCreateClientRequest request) {
        // Lookup by email first
        if (request.email() != null) {
            Optional<Client> byEmail = clientPersistencePort.findByTenantIdAndEmail(tenantId, request.email());
            if (byEmail.isPresent()) {
                log.atDebug().addKeyValue("email", request.email()).log("Client found by email");
                return mapper.toInternalResponse(byEmail.get());
            }
        }

        // Lookup by phone
        if (request.phone() != null) {
            Optional<Client> byPhone = clientPersistencePort.findByTenantIdAndPhone(tenantId, request.phone());
            if (byPhone.isPresent()) {
                log.atDebug().addKeyValue("phone", request.phone()).log("Client found by phone");
                return mapper.toInternalResponse(byPhone.get());
            }
        }

        // Create new client
        Client client = Client.builder()
                .externalId(ExternalIdGenerator.generate("cli"))
                .tenantId(tenantId)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phone(request.phone())
                .source(ClientSource.ONLINE_BOOKING)
                .totalVisits(0)
                .gdprConsentAt(Instant.now())
                .active(true)
                .build();

        Client saved = clientPersistencePort.save(client);
        log.atInfo().addKeyValue("externalId", saved.getExternalId()).log("Client created via find-or-create");
        return mapper.toInternalResponse(saved);
    }
}
