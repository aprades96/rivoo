package com.rivoo.appointment.infrastructure.adapter.out.persistence;

import com.rivoo.appointment.domain.model.AppointmentSource;
import com.rivoo.appointment.domain.model.AppointmentStatus;
import com.rivoo.appointment.infrastructure.adapter.out.persistence.entity.AppointmentJpaEntity;
import com.rivoo.appointment.infrastructure.adapter.out.persistence.repository.AppointmentJpaRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integración de la capa de persistencia de appointment-service.
 *
 * Usa Testcontainers para levantar MySQL 8.0 real. Flyway aplica las
 * migraciones antes del primer test. El contexto de Spring Boot completo se
 * levanta una sola vez para todos los tests de la clase (compartido).
 *
 * Estrategia de aislamiento multi-tenant:
 * - TenantFilterAspect intercepta cada llamada al repositorio y activa el
 *   Hibernate @Filter "tenantFilter" con el tenantId del TenantContext.
 * - @BeforeEach fija TenantContext a "tenant_test" y @AfterEach lo limpia,
 *   replicando lo que TenantInterceptor haría en una petición HTTP real.
 * - Las entidades llevan tenantId = "tenant_test" establecido directamente,
 *   por lo que TenantEntityListener (@PrePersist) actúa como no-op.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
class AppointmentRepositoryIntegrationTest {

    private static final String TENANT = "tenant_test";
    private static final String OTHER_TENANT = "tenant_other";

    // ------------------------------------------------------------------ //
    // Testcontainer — @ServiceConnection configura automáticamente la URL  //
    // de datasource, usuario y contraseña sin necesidad de propiedades     //
    // adicionales en application-test.yml.                                 //
    // ------------------------------------------------------------------ //
    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("appointment_db")
            .withUrlParam("useSSL", "false")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("serverTimezone", "UTC");

    @Autowired
    private AppointmentJpaRepository repository;

    // ------------------------------------------------------------------ //
    // Fixtures de tiempo                                                   //
    // ------------------------------------------------------------------ //

    /** Instante base: 2026-03-25 09:00:00 UTC */
    private static final Instant BASE_TIME = Instant.parse("2026-03-25T09:00:00Z");

    // ------------------------------------------------------------------ //
    // Setup / Teardown                                                     //
    // ------------------------------------------------------------------ //

    @BeforeEach
    void setUp() {
        // Simula lo que TenantInterceptor hace al inicio de cada petición HTTP.
        TenantContext.setCurrentTenantId(TENANT);
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // Simula el afterCompletion del TenantInterceptor.
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ //
    // Helpers                                                              //
    // ------------------------------------------------------------------ //

    /**
     * Construye una entidad mínima y válida. Todos los campos NOT NULL están
     * cubiertos. El tenantId se establece explícitamente para que
     * TenantEntityListener no necesite el TenantContext.
     */
    private AppointmentJpaEntity buildEntity(String externalId,
                                              Instant startTime,
                                              Instant endTime,
                                              AppointmentStatus status) {
        AppointmentJpaEntity e = new AppointmentJpaEntity();
        e.setExternalId(externalId);
        e.setTenantId(TENANT);
        e.setClientId("cli_" + UUID.randomUUID());
        e.setClientName("Test Client");
        e.setClientPhone("+34600000000");
        e.setClientEmail("client@test.com");
        e.setEmployeeId("emp_test");
        e.setEmployeeName("Test Employee");
        e.setServiceId("svc_test");
        e.setServiceName("Test Service");
        e.setServicePrice(new BigDecimal("25.00"));
        e.setServiceDurationMinutes(30);
        e.setStartTime(startTime);
        e.setEndTime(endTime);
        e.setStatus(status);
        e.setSource(AppointmentSource.MANUAL);
        e.setReminderSent(false);
        return e;
    }

    /** Variante conveniente con tenantId configurable (para tests cross-tenant). */
    private AppointmentJpaEntity buildEntityForTenant(String tenantId,
                                                       String externalId,
                                                       Instant startTime,
                                                       Instant endTime,
                                                       AppointmentStatus status) {
        AppointmentJpaEntity e = buildEntity(externalId, startTime, endTime, status);
        e.setTenantId(tenantId);
        return e;
    }

    private String newExternalId() {
        return "apt_" + UUID.randomUUID();
    }

    // ------------------------------------------------------------------ //
    // Tests                                                                //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("flyway_migration_appliedSuccessfully — la tabla 'appointments' existe con el esquema correcto")
    void flyway_migration_appliedSuccessfully() {
        // Si @SpringBootTest arranca sin errores de Flyway/JPA validate, el
        // esquema es correcto. Este test confirma también que el repositorio
        // está operativo.
        long count = repository.count();
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("save_and_findByExternalId — guarda una entidad y la recupera por externalId")
    void save_and_findByExternalId() {
        String externalId = newExternalId();
        AppointmentJpaEntity saved = repository.save(
                buildEntity(externalId, BASE_TIME, BASE_TIME.plus(30, ChronoUnit.MINUTES),
                        AppointmentStatus.PENDING));

        Optional<AppointmentJpaEntity> found = repository.findByExternalId(externalId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getExternalId()).isEqualTo(externalId);
        assertThat(found.get().getTenantId()).isEqualTo(TENANT);
        assertThat(found.get().getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(found.get().getServicePrice()).isEqualByComparingTo("25.00");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("findByFilters_withEmployeeId — devuelve solo las citas del empleado indicado")
    void findByFilters_withEmployeeId() {
        String empA = "emp_alpha";
        String empB = "emp_beta";

        AppointmentJpaEntity a1 = buildEntity(newExternalId(), BASE_TIME, BASE_TIME.plus(30, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED);
        a1.setEmployeeId(empA);
        a1.setEmployeeName("Alpha Employee");

        AppointmentJpaEntity a2 = buildEntity(newExternalId(), BASE_TIME.plus(1, ChronoUnit.HOURS), BASE_TIME.plus(90, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED);
        a2.setEmployeeId(empA);
        a2.setEmployeeName("Alpha Employee");

        AppointmentJpaEntity a3 = buildEntity(newExternalId(), BASE_TIME.plus(2, ChronoUnit.HOURS), BASE_TIME.plus(150, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED);
        a3.setEmployeeId(empB);
        a3.setEmployeeName("Beta Employee");

        repository.saveAll(List.of(a1, a2, a3));

        Page<AppointmentJpaEntity> result = repository.findByFilters(
                empA, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(AppointmentJpaEntity::getEmployeeId)
                .containsOnly(empA);
    }

    @Test
    @DisplayName("findByFilters_withDateRange — devuelve solo las citas dentro del rango de fechas")
    void findByFilters_withDateRange() {
        // Día 25: cita en BASE_TIME (09:00)
        AppointmentJpaEntity day25 = buildEntity(newExternalId(),
                BASE_TIME, BASE_TIME.plus(30, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED);

        // Día 26: cita en BASE_TIME + 24h
        AppointmentJpaEntity day26 = buildEntity(newExternalId(),
                BASE_TIME.plus(1, ChronoUnit.DAYS), BASE_TIME.plus(1, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES),
                AppointmentStatus.CONFIRMED);

        // Día 27: cita en BASE_TIME + 48h
        AppointmentJpaEntity day27 = buildEntity(newExternalId(),
                BASE_TIME.plus(2, ChronoUnit.DAYS), BASE_TIME.plus(2, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES),
                AppointmentStatus.CONFIRMED);

        repository.saveAll(List.of(day25, day26, day27));

        // Rango: desde inicio del día 25 hasta fin del día 26 (excluye día 27)
        Instant rangeStart = Instant.parse("2026-03-25T00:00:00Z");
        Instant rangeEnd   = Instant.parse("2026-03-27T00:00:00Z");

        Page<AppointmentJpaEntity> result = repository.findByFilters(
                null, rangeStart, rangeEnd, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(AppointmentJpaEntity::getStartTime)
                .doesNotContain(day27.getStartTime());
    }

    @Test
    @DisplayName("findByFilters_withStatus — filtra por estado exacto")
    void findByFilters_withStatus() {
        repository.save(buildEntity(newExternalId(), BASE_TIME, BASE_TIME.plus(30, ChronoUnit.MINUTES), AppointmentStatus.PENDING));
        repository.save(buildEntity(newExternalId(), BASE_TIME.plus(1, ChronoUnit.HOURS), BASE_TIME.plus(90, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED));
        repository.save(buildEntity(newExternalId(), BASE_TIME.plus(2, ChronoUnit.HOURS), BASE_TIME.plus(150, ChronoUnit.MINUTES), AppointmentStatus.CANCELLED));

        Page<AppointmentJpaEntity> confirmed = repository.findByFilters(
                null, null, null, AppointmentStatus.CONFIRMED, PageRequest.of(0, 10));

        assertThat(confirmed.getTotalElements()).isEqualTo(1);
        assertThat(confirmed.getContent().get(0).getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    @Transactional
    @DisplayName("findOverlappingForUpdate_detectsConflict — detecta solapamientos y los distingue de franjas libres")
    void findOverlappingForUpdate_detectsConflict() {
        // Cita existente: 09:00 – 09:30
        AppointmentJpaEntity existing = buildEntity(newExternalId(),
                BASE_TIME, BASE_TIME.plus(30, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED);
        repository.save(existing);

        List<AppointmentStatus> excluded = List.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW);

        // Caso 1: nueva cita 09:15 – 09:45 → SOLAPAMIENTO
        List<AppointmentJpaEntity> overlapping = repository.findOverlappingForUpdate(
                TENANT,
                "emp_test",
                BASE_TIME.plus(15, ChronoUnit.MINUTES),
                BASE_TIME.plus(45, ChronoUnit.MINUTES),
                excluded);

        assertThat(overlapping).hasSize(1);
        assertThat(overlapping.get(0).getExternalId()).isEqualTo(existing.getExternalId());

        // Caso 2: nueva cita 09:30 – 10:00 → SIN SOLAPAMIENTO (empieza justo cuando termina la anterior)
        List<AppointmentJpaEntity> noOverlap = repository.findOverlappingForUpdate(
                TENANT,
                "emp_test",
                BASE_TIME.plus(30, ChronoUnit.MINUTES),
                BASE_TIME.plus(60, ChronoUnit.MINUTES),
                excluded);

        assertThat(noOverlap).isEmpty();

        // Caso 3: nueva cita 11:00 – 11:30 → SIN SOLAPAMIENTO
        List<AppointmentJpaEntity> farFuture = repository.findOverlappingForUpdate(
                TENANT,
                "emp_test",
                BASE_TIME.plus(2, ChronoUnit.HOURS),
                BASE_TIME.plus(150, ChronoUnit.MINUTES),
                excluded);

        assertThat(farFuture).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("findOverlappingForUpdate_ignoresCancelledAndNoShow — no cuenta citas canceladas como conflicto")
    void findOverlappingForUpdate_ignoresCancelledAndNoShow() {
        // Cita CANCELLED que se solapa en tiempo
        AppointmentJpaEntity cancelled = buildEntity(newExternalId(),
                BASE_TIME, BASE_TIME.plus(30, ChronoUnit.MINUTES), AppointmentStatus.CANCELLED);
        // Cita NO_SHOW que se solapa en tiempo
        AppointmentJpaEntity noShow = buildEntity(newExternalId(),
                BASE_TIME, BASE_TIME.plus(30, ChronoUnit.MINUTES), AppointmentStatus.NO_SHOW);
        repository.saveAll(List.of(cancelled, noShow));

        List<AppointmentJpaEntity> result = repository.findOverlappingForUpdate(
                TENANT, "emp_test",
                BASE_TIME.plus(15, ChronoUnit.MINUTES),
                BASE_TIME.plus(45, ChronoUnit.MINUTES),
                List.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW));

        assertThat(result).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("findByEmployeeAndDateRange_excludesCancelled — devuelve solo citas activas del empleado en el día")
    void findByEmployeeAndDateRange_excludesCancelled() {
        Instant startOfDay = Instant.parse("2026-03-25T00:00:00Z");
        Instant endOfDay   = Instant.parse("2026-03-26T00:00:00Z");

        AppointmentJpaEntity confirmed = buildEntity(newExternalId(),
                BASE_TIME, BASE_TIME.plus(30, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED);
        AppointmentJpaEntity pending = buildEntity(newExternalId(),
                BASE_TIME.plus(1, ChronoUnit.HOURS), BASE_TIME.plus(90, ChronoUnit.MINUTES), AppointmentStatus.PENDING);
        AppointmentJpaEntity cancelled = buildEntity(newExternalId(),
                BASE_TIME.plus(2, ChronoUnit.HOURS), BASE_TIME.plus(150, ChronoUnit.MINUTES), AppointmentStatus.CANCELLED);
        repository.saveAll(List.of(confirmed, pending, cancelled));

        List<AppointmentJpaEntity> result = repository.findByEmployeeAndDateRange(
                TENANT,
                "emp_test",
                startOfDay,
                endOfDay,
                List.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW));

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(AppointmentJpaEntity::getStatus)
                .containsExactlyInAnyOrder(AppointmentStatus.CONFIRMED, AppointmentStatus.PENDING);
    }

    @Test
    @DisplayName("findByEmployeeAndDateRange_orderedByStartTime — resultado ordenado ascendente")
    void findByEmployeeAndDateRange_orderedByStartTime() {
        Instant startOfDay = Instant.parse("2026-03-25T00:00:00Z");
        Instant endOfDay   = Instant.parse("2026-03-26T00:00:00Z");

        // Guardamos en orden inverso al que esperamos en el resultado
        AppointmentJpaEntity third = buildEntity(newExternalId(),
                BASE_TIME.plus(2, ChronoUnit.HOURS), BASE_TIME.plus(150, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED);
        AppointmentJpaEntity first = buildEntity(newExternalId(),
                BASE_TIME, BASE_TIME.plus(30, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED);
        AppointmentJpaEntity second = buildEntity(newExternalId(),
                BASE_TIME.plus(1, ChronoUnit.HOURS), BASE_TIME.plus(90, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED);
        repository.saveAll(List.of(third, first, second));

        List<AppointmentJpaEntity> result = repository.findByEmployeeAndDateRange(
                TENANT, "emp_test", startOfDay, endOfDay, List.of());

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getStartTime()).isEqualTo(first.getStartTime());
        assertThat(result.get(1).getStartTime()).isEqualTo(second.getStartTime());
        assertThat(result.get(2).getStartTime()).isEqualTo(third.getStartTime());
    }

    @Test
    @DisplayName("countByTenantAndMonth — cuenta citas activas del mes excluyendo canceladas")
    void countByTenantAndMonth() {
        Instant monthStart = Instant.parse("2026-03-01T00:00:00Z");
        Instant monthEnd   = Instant.parse("2026-04-01T00:00:00Z");

        // 3 citas en marzo: 2 CONFIRMED + 1 CANCELLED
        repository.save(buildEntity(newExternalId(), BASE_TIME, BASE_TIME.plus(30, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED));
        repository.save(buildEntity(newExternalId(), BASE_TIME.plus(1, ChronoUnit.HOURS), BASE_TIME.plus(90, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED));
        repository.save(buildEntity(newExternalId(), BASE_TIME.plus(2, ChronoUnit.HOURS), BASE_TIME.plus(150, ChronoUnit.MINUTES), AppointmentStatus.CANCELLED));

        // 1 cita fuera del mes (abril)
        repository.save(buildEntity(newExternalId(),
                Instant.parse("2026-04-01T09:00:00Z"),
                Instant.parse("2026-04-01T09:30:00Z"),
                AppointmentStatus.CONFIRMED));

        long count = repository.countByTenantAndMonth(
                TENANT, monthStart, monthEnd,
                List.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW));

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("findByClientIdAndTenantId — recupera todas las citas de un cliente dentro del tenant")
    void findByClientIdAndTenantId() {
        String clientA = "cli_alpha-001";
        String clientB = "cli_beta-002";

        AppointmentJpaEntity ca1 = buildEntity(newExternalId(), BASE_TIME, BASE_TIME.plus(30, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED);
        ca1.setClientId(clientA);

        AppointmentJpaEntity ca2 = buildEntity(newExternalId(), BASE_TIME.plus(1, ChronoUnit.HOURS), BASE_TIME.plus(90, ChronoUnit.MINUTES), AppointmentStatus.COMPLETED);
        ca2.setClientId(clientA);

        AppointmentJpaEntity cb1 = buildEntity(newExternalId(), BASE_TIME.plus(2, ChronoUnit.HOURS), BASE_TIME.plus(150, ChronoUnit.MINUTES), AppointmentStatus.PENDING);
        cb1.setClientId(clientB);

        repository.saveAll(List.of(ca1, ca2, cb1));

        List<AppointmentJpaEntity> result = repository.findByClientIdAndTenantId(clientA, TENANT);

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(AppointmentJpaEntity::getClientId)
                .containsOnly(clientA);
    }

    @Test
    @DisplayName("countByStatusGrouped — agrupa y cuenta por estado dentro del mes")
    void countByStatusGrouped() {
        Instant monthStart = Instant.parse("2026-03-01T00:00:00Z");
        Instant monthEnd   = Instant.parse("2026-04-01T00:00:00Z");

        repository.save(buildEntity(newExternalId(), BASE_TIME, BASE_TIME.plus(30, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED));
        repository.save(buildEntity(newExternalId(), BASE_TIME.plus(1, ChronoUnit.HOURS), BASE_TIME.plus(90, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED));
        repository.save(buildEntity(newExternalId(), BASE_TIME.plus(2, ChronoUnit.HOURS), BASE_TIME.plus(150, ChronoUnit.MINUTES), AppointmentStatus.CANCELLED));
        repository.save(buildEntity(newExternalId(), BASE_TIME.plus(3, ChronoUnit.HOURS), BASE_TIME.plus(210, ChronoUnit.MINUTES), AppointmentStatus.PENDING));

        List<Object[]> grouped = repository.countByStatusGrouped(TENANT, monthStart, monthEnd);

        // Debe haber 3 grupos distintos (CONFIRMED x2, CANCELLED x1, PENDING x1)
        assertThat(grouped).hasSize(3);

        // Verificamos que la suma total es 4
        long total = grouped.stream()
                .mapToLong(row -> (Long) row[1])
                .sum();
        assertThat(total).isEqualTo(4);
    }

    @Test
    @DisplayName("countBySourceGrouped — agrupa y cuenta por fuente de origen")
    void countBySourceGrouped() {
        Instant monthStart = Instant.parse("2026-03-01T00:00:00Z");
        Instant monthEnd   = Instant.parse("2026-04-01T00:00:00Z");

        AppointmentJpaEntity manual = buildEntity(newExternalId(), BASE_TIME, BASE_TIME.plus(30, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED);
        manual.setSource(AppointmentSource.MANUAL);

        AppointmentJpaEntity online = buildEntity(newExternalId(), BASE_TIME.plus(1, ChronoUnit.HOURS), BASE_TIME.plus(90, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED);
        online.setSource(AppointmentSource.ONLINE);

        AppointmentJpaEntity phone = buildEntity(newExternalId(), BASE_TIME.plus(2, ChronoUnit.HOURS), BASE_TIME.plus(150, ChronoUnit.MINUTES), AppointmentStatus.CONFIRMED);
        phone.setSource(AppointmentSource.PHONE);

        repository.saveAll(List.of(manual, online, phone));

        List<Object[]> grouped = repository.countBySourceGrouped(TENANT, monthStart, monthEnd);

        assertThat(grouped).hasSize(3);
        long total = grouped.stream()
                .mapToLong(row -> (Long) row[1])
                .sum();
        assertThat(total).isEqualTo(3);
    }

    @Test
    @DisplayName("prePersist_setsTimestamps — @PrePersist establece createdAt y updatedAt")
    void prePersist_setsTimestamps() {
        Instant before = Instant.now().minusSeconds(1);

        AppointmentJpaEntity saved = repository.save(
                buildEntity(newExternalId(), BASE_TIME, BASE_TIME.plus(30, ChronoUnit.MINUTES), AppointmentStatus.PENDING));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isAfterOrEqualTo(before);
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(before);
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    @DisplayName("tenantEntityListener — auto-asigna tenantId desde TenantContext cuando la entidad no lo tiene")
    void tenantEntityListener_autoSetsTenantIdFromContext() {
        // Creamos una entidad SIN tenantId explícito para que el listener lo inyecte
        AppointmentJpaEntity entity = new AppointmentJpaEntity();
        entity.setExternalId(newExternalId());
        // tenantId = null → TenantEntityListener lo tomará de TenantContext
        entity.setClientName("Listener Test Client");
        entity.setEmployeeId("emp_test");
        entity.setEmployeeName("Test Employee");
        entity.setServiceId("svc_test");
        entity.setServiceName("Test Service");
        entity.setServicePrice(new BigDecimal("30.00"));
        entity.setServiceDurationMinutes(45);
        entity.setStartTime(BASE_TIME.plus(5, ChronoUnit.HOURS));
        entity.setEndTime(BASE_TIME.plus(5, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES));
        entity.setStatus(AppointmentStatus.PENDING);
        entity.setSource(AppointmentSource.WALK_IN);
        entity.setReminderSent(false);

        AppointmentJpaEntity saved = repository.save(entity);

        assertThat(saved.getTenantId()).isEqualTo(TENANT);
    }
}
