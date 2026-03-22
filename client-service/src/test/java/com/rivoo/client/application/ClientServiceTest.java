package com.rivoo.client.application;

import com.rivoo.client.application.dto.ClientResponse;
import com.rivoo.client.application.dto.CreateClientRequest;
import com.rivoo.client.domain.exception.ClientAlreadyAnonymizedException;
import com.rivoo.client.domain.exception.ClientNotFoundException;
import com.rivoo.client.domain.exception.DuplicateClientEmailException;
import com.rivoo.client.domain.model.Client;
import com.rivoo.client.domain.model.ClientSource;
import com.rivoo.client.domain.port.out.AppointmentServicePort;
import com.rivoo.client.domain.port.out.ClientPersistencePort;
import com.rivoo.client.infrastructure.mapper.ClientDtoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientPersistencePort clientPersistencePort;

    @Mock
    private AppointmentServicePort appointmentServicePort;

    @Mock
    private ClientDtoMapper mapper;

    private ClientService clientService;

    private static final String TENANT_ID = "sal_tenant-001";
    private static final String CLIENT_EXTERNAL_ID = "cli_abc123";

    @BeforeEach
    void setUp() {
        clientService = new ClientService(clientPersistencePort, appointmentServicePort, mapper);
    }

    // ── create: happy path ───────────────────────────────────────────────

    @Test
    void create_happyPath_savesClientWithGeneratedExternalId() {
        when(clientPersistencePort.existsByTenantIdAndEmail(TENANT_ID, "ana@gmail.com")).thenReturn(false);
        when(clientPersistencePort.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Client.class))).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            return new ClientResponse(c.getExternalId(), c.getFirstName(), c.getLastName(),
                    c.getEmail(), c.getPhone(), null, "WALK_IN", null,
                    0, null, true, Instant.now(), Instant.now());
        });

        CreateClientRequest request = new CreateClientRequest(
                "Ana", "Lopez", "ana@gmail.com", "+34 600 111 222", null, null, null);

        ClientResponse response = clientService.create(TENANT_ID, request);

        assertThat(response.firstName()).isEqualTo("Ana");
        assertThat(response.active()).isTrue();

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientPersistencePort).save(captor.capture());
        Client saved = captor.getValue();

        assertThat(saved.getExternalId()).startsWith("cli_");
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getGdprConsentAt()).isNotNull();
    }

    @Test
    void create_noEmailProvided_savesWithoutEmailCheck() {
        when(clientPersistencePort.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Client.class))).thenReturn(dummyResponse());

        CreateClientRequest request = new CreateClientRequest(
                "Luis", "Martinez", null, "+34 600 000 999", null, null, null);

        clientService.create(TENANT_ID, request);

        verify(clientPersistencePort, never()).existsByTenantIdAndEmail(anyString(), anyString());
    }

    @Test
    void create_nullSource_defaultsToWalkIn() {
        when(clientPersistencePort.existsByTenantIdAndEmail(TENANT_ID, "pedro@gmail.com")).thenReturn(false);
        when(clientPersistencePort.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(dummyResponse());

        CreateClientRequest request = new CreateClientRequest(
                "Pedro", "Garcia", "pedro@gmail.com", null, null, null, null);

        clientService.create(TENANT_ID, request);

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientPersistencePort).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(ClientSource.WALK_IN);
    }

    // ── create: duplicate email → throws ────────────────────────────────

    @Test
    void create_duplicateEmail_throwsDuplicateClientEmailException() {
        when(clientPersistencePort.existsByTenantIdAndEmail(TENANT_ID, "duplicate@salon.com")).thenReturn(true);

        CreateClientRequest request = new CreateClientRequest(
                "Maria", "Vidal", "duplicate@salon.com", null, null, null, null);

        assertThatThrownBy(() -> clientService.create(TENANT_ID, request))
                .isInstanceOf(DuplicateClientEmailException.class);

        verify(clientPersistencePort, never()).save(any());
    }

    // ── anonymize: happy path ────────────────────────────────────────────

    @Test
    void anonymize_happyPath_setsAnonymizedFieldsAndDeactivates() {
        Client client = buildActiveClient();
        when(clientPersistencePort.findByExternalId(CLIENT_EXTERNAL_ID)).thenReturn(Optional.of(client));
        when(clientPersistencePort.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        clientService.anonymize(CLIENT_EXTERNAL_ID);

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientPersistencePort).save(captor.capture());
        Client saved = captor.getValue();

        assertThat(saved.getFirstName()).isEqualTo("ANONYMIZED");
        assertThat(saved.getLastName()).isEqualTo("CLIENT");
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getNotes()).isNull();
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getGdprAnonymizedAt()).isNotNull();
        assertThat(saved.isAnonymized()).isTrue();
    }

    @Test
    void anonymize_clientNotFound_throwsClientNotFoundException() {
        when(clientPersistencePort.findByExternalId(CLIENT_EXTERNAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.anonymize(CLIENT_EXTERNAL_ID))
                .isInstanceOf(ClientNotFoundException.class);

        verify(clientPersistencePort, never()).save(any());
    }

    // ── anonymize: already anonymized → throws ────────────────────────────

    @Test
    void anonymize_alreadyAnonymized_throwsClientAlreadyAnonymizedException() {
        Client alreadyAnonymized = buildAlreadyAnonymizedClient();
        when(clientPersistencePort.findByExternalId(CLIENT_EXTERNAL_ID))
                .thenReturn(Optional.of(alreadyAnonymized));

        assertThatThrownBy(() -> clientService.anonymize(CLIENT_EXTERNAL_ID))
                .isInstanceOf(ClientAlreadyAnonymizedException.class);

        verify(clientPersistencePort, never()).save(any());
    }

    // ── getByExternalId: not found → throws ──────────────────────────────

    @Test
    void getByExternalId_notFound_throwsClientNotFoundException() {
        when(clientPersistencePort.findByExternalId("cli_notexists")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.getByExternalId("cli_notexists"))
                .isInstanceOf(ClientNotFoundException.class);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Client buildActiveClient() {
        return Client.builder()
                .id(1L)
                .externalId(CLIENT_EXTERNAL_ID)
                .tenantId(TENANT_ID)
                .firstName("Maria")
                .lastName("Garcia")
                .email("maria@salon.com")
                .phone("+34 600 111 222")
                .notes("Cliente VIP")
                .source(ClientSource.WALK_IN)
                .totalVisits(5)
                .gdprConsentAt(Instant.now().minusSeconds(86400))
                .active(true)
                .build();
    }

    private Client buildAlreadyAnonymizedClient() {
        Client client = buildActiveClient();
        client.anonymize(); // uses the domain method, sets gdprAnonymizedAt
        return client;
    }

    private ClientResponse dummyResponse() {
        return new ClientResponse(CLIENT_EXTERNAL_ID, "Ana", "Lopez",
                "ana@gmail.com", null, null, "WALK_IN", null,
                0, null, true, Instant.now(), Instant.now());
    }
}
