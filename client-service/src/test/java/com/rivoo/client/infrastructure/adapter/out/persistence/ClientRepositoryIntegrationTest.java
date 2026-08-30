package com.rivoo.client.infrastructure.adapter.out.persistence;

import com.rivoo.client.domain.model.ClientSource;
import com.rivoo.client.infrastructure.adapter.out.persistence.entity.ClientJpaEntity;
import com.rivoo.client.infrastructure.adapter.out.persistence.repository.ClientJpaRepository;
import com.rivoo.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ClientJpaRepository#search}, the query backing
 * {@code GET /api/v1/clients?search=...}.
 *
 * Uses Testcontainers to run against a real MySQL 8.0 instance so the JPQL
 * query, the Hibernate {@code tenantFilter} and MySQL's NULL-sorting
 * semantics are all exercised for real, instead of being simulated through a
 * mocked {@code ClientPersistencePort} (whose {@code thenReturn} would decide
 * the order itself and prove nothing about the query).
 *
 * Multi-tenant isolation strategy (mirrors AppointmentRepositoryIntegrationTest):
 * - {@code TenantFilterAspect} activates the Hibernate "tenantFilter" on every
 *   repository call using the {@code TenantContext} ThreadLocal.
 * - {@code @BeforeEach} sets {@code TenantContext} to "tenant_test" and
 *   {@code @AfterEach} clears it, replicating what {@code TenantInterceptor}
 *   does on a real HTTP request.
 * - Entities set {@code tenantId} explicitly so {@code TenantEntityListener}
 *   is a no-op.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
class ClientRepositoryIntegrationTest {

    private static final String TENANT = "tenant_test";
    private static final String OTHER_TENANT = "tenant_other";

    // ------------------------------------------------------------------ //
    // Testcontainer — @ServiceConnection configures datasource URL, user  //
    // and password automatically, no extra application-test.yml needed.  //
    // ------------------------------------------------------------------ //
    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("client_db")
            .withUrlParam("useSSL", "false")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("serverTimezone", "UTC");

    @Autowired
    private ClientJpaRepository repository;

    // ------------------------------------------------------------------ //
    // Setup / Teardown                                                     //
    // ------------------------------------------------------------------ //

    @BeforeEach
    void setUp() {
        // Simulates what TenantInterceptor does at the start of every HTTP request.
        TenantContext.setCurrentTenantId(TENANT);
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // Simulates TenantInterceptor's afterCompletion.
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ //
    // Helpers                                                              //
    // ------------------------------------------------------------------ //

    /** Builds a minimal, valid entity for {@link #TENANT}. All NOT NULL columns are covered. */
    private ClientJpaEntity buildEntity(String externalId, String firstName, String lastName,
                                         String email, String phone, Instant lastVisitAt) {
        return buildEntityForTenant(TENANT, externalId, firstName, lastName, email, phone, lastVisitAt);
    }

    /** Variant with a configurable tenantId, for cross-tenant isolation tests. */
    private ClientJpaEntity buildEntityForTenant(String tenantId, String externalId, String firstName,
                                                  String lastName, String email, String phone,
                                                  Instant lastVisitAt) {
        ClientJpaEntity e = new ClientJpaEntity();
        e.setExternalId(externalId);
        e.setTenantId(tenantId);
        e.setFirstName(firstName);
        e.setLastName(lastName);
        e.setEmail(email);
        e.setPhone(phone);
        e.setSource(ClientSource.WALK_IN);
        e.setTotalVisits(0);
        e.setLastVisitAt(lastVisitAt);
        e.setGdprConsentAt(Instant.now());
        e.setActive(true);
        return e;
    }

    private String newExternalId() {
        return "cli_" + UUID.randomUUID();
    }

    // ------------------------------------------------------------------ //
    // (a) search null / blank / whitespace-only → returns everything      //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("search_nullSearch_returnsAllClients")
    void search_nullSearch_returnsAllClients() {
        repository.saveAll(List.of(
                buildEntity(newExternalId(), "Ana", "Lopez", "ana@test.com", "+34600000001", null),
                buildEntity(newExternalId(), "Luis", "Garcia", "luis@test.com", "+34600000002", null),
                buildEntity(newExternalId(), "Marta", "Ruiz", "marta@test.com", "+34600000003", null)));

        Page<ClientJpaEntity> result = repository.search(null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("search_blankOrWhitespaceOnlySearch_returnsAllClients")
    void search_blankOrWhitespaceOnlySearch_returnsAllClients() {
        repository.saveAll(List.of(
                buildEntity(newExternalId(), "Ana", "Lopez", "ana@test.com", "+34600000001", null),
                buildEntity(newExternalId(), "Luis", "Garcia", "luis@test.com", "+34600000002", null)));

        assertThat(repository.search("", PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
        assertThat(repository.search("   ", PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ //
    // (b) search matches first name, last name, phone and email — case   //
    //     insensitive substring                                          //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("search_matchesFirstNameCaseInsensitiveSubstring")
    void search_matchesFirstNameCaseInsensitiveSubstring() {
        ClientJpaEntity target = buildEntity(newExternalId(), "Anabella", "Soler", "anabella@test.com", "+34600000010", null);
        repository.saveAll(List.of(
                target,
                buildEntity(newExternalId(), "Pedro", "Diaz", "pedro@test.com", "+34600000011", null)));

        Page<ClientJpaEntity> result = repository.search("naBEL", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(ClientJpaEntity::getExternalId)
                .containsExactly(target.getExternalId());
    }

    @Test
    @DisplayName("search_matchesLastNameCaseInsensitiveSubstring")
    void search_matchesLastNameCaseInsensitiveSubstring() {
        ClientJpaEntity target = buildEntity(newExternalId(), "Carlos", "Fernandez", "carlos@test.com", "+34600000012", null);
        repository.saveAll(List.of(
                target,
                buildEntity(newExternalId(), "Eva", "Molina", "eva@test.com", "+34600000013", null)));

        Page<ClientJpaEntity> result = repository.search("NANDE", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(ClientJpaEntity::getExternalId)
                .containsExactly(target.getExternalId());
    }

    @Test
    @DisplayName("search_matchesPhoneSubstring")
    void search_matchesPhoneSubstring() {
        ClientJpaEntity target = buildEntity(newExternalId(), "Nora", "Vega", "nora@test.com", "+34611223344", null);
        repository.saveAll(List.of(
                target,
                buildEntity(newExternalId(), "Iker", "Santos", "iker@test.com", "+34699887766", null)));

        Page<ClientJpaEntity> result = repository.search("611223", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(ClientJpaEntity::getExternalId)
                .containsExactly(target.getExternalId());
    }

    @Test
    @DisplayName("search_matchesEmailCaseInsensitiveSubstring")
    void search_matchesEmailCaseInsensitiveSubstring() {
        ClientJpaEntity target = buildEntity(newExternalId(), "Oscar", "Prat", "OSCAR.PRAT@Salon.COM", "+34600000020", null);
        repository.saveAll(List.of(
                target,
                buildEntity(newExternalId(), "Julia", "Rey", "julia@other.com", "+34600000021", null)));

        Page<ClientJpaEntity> result = repository.search("prat@salon", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(ClientJpaEntity::getExternalId)
                .containsExactly(target.getExternalId());
    }

    @Test
    @DisplayName("search_noMatch_returnsEmptyPage")
    void search_noMatch_returnsEmptyPage() {
        repository.save(buildEntity(newExternalId(), "Ana", "Lopez", "ana@test.com", "+34600000001", null));

        Page<ClientJpaEntity> result = repository.search("zzz-no-such-client", PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    // ------------------------------------------------------------------ //
    // (c) order is lastVisitAt DESC, createdAt DESC — a client with a     //
    //     null lastVisitAt sorts last                                     //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("search_ordersByLastVisitAtDescThenCreatedAtDesc_nullLastVisitAtLast")
    void search_ordersByLastVisitAtDescThenCreatedAtDesc_nullLastVisitAtLast() {
        Instant now = Instant.now();

        ClientJpaEntity mostRecentVisit = buildEntity(newExternalId(), "Recent", "Visitor",
                "recent@test.com", "+34600000030", now.minus(1, ChronoUnit.DAYS));
        ClientJpaEntity olderVisit = buildEntity(newExternalId(), "Older", "Visitor",
                "older@test.com", "+34600000031", now.minus(10, ChronoUnit.DAYS));
        ClientJpaEntity neverVisited = buildEntity(newExternalId(), "Never", "Visited",
                "never@test.com", "+34600000032", null);

        // Save in an order deliberately different from the expected result.
        repository.saveAll(List.of(neverVisited, olderVisit, mostRecentVisit));

        Page<ClientJpaEntity> result = repository.search(null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(ClientJpaEntity::getExternalId)
                .containsExactly(
                        mostRecentVisit.getExternalId(),
                        olderVisit.getExternalId(),
                        neverVisited.getExternalId());
    }

    // ------------------------------------------------------------------ //
    // (d) tenant isolation — a matching client from another tenant never  //
    //     shows up                                                       //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("search_tenantIsolation_excludesMatchingClientFromOtherTenant")
    void search_tenantIsolation_excludesMatchingClientFromOtherTenant() {
        ClientJpaEntity ownTenantClient = buildEntityForTenant(TENANT, newExternalId(),
                "Sara", "Campos", "sara@test.com", "+34600000040", null);
        ClientJpaEntity otherTenantClient = buildEntityForTenant(OTHER_TENANT, newExternalId(),
                "Sara", "Campos", "sara.other@test.com", "+34600000041", null);

        repository.saveAll(List.of(ownTenantClient, otherTenantClient));

        Page<ClientJpaEntity> result = repository.search("Sara", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(ClientJpaEntity::getExternalId)
                .containsExactly(ownTenantClient.getExternalId());
    }
}
