package com.rivoo.staff.infrastructure.adapter.out.persistence.adapter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rivoo.staff.domain.model.EmployeeServiceAssignment;
import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.EmployeeServiceJpaEntity;
import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.ServiceOfferingJpaEntity;
import com.rivoo.staff.infrastructure.adapter.out.persistence.repository.EmployeeServiceJpaRepository;
import com.rivoo.staff.infrastructure.adapter.out.persistence.repository.ServiceOfferingJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServicePersistenceAdapterTest {

    @Mock
    private EmployeeServiceJpaRepository repository;

    @Mock
    private ServiceOfferingJpaRepository serviceRepository;

    @Mock
    private EntityManager entityManager;

    private EmployeeServicePersistenceAdapter adapter;
    private ListAppender<ILoggingEvent> logAppender;

    private static final Long EMPLOYEE_ID = 42L;
    private static final Long LIVE_SERVICE_ID = 1L;
    private static final Long ORPHANED_SERVICE_ID = 999L;

    @BeforeEach
    void setUp() {
        adapter = new EmployeeServicePersistenceAdapter(repository, serviceRepository, entityManager);

        logAppender = new ListAppender<>();
        logAppender.start();
        adapterLogger().addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        adapterLogger().detachAppender(logAppender);
    }

    private static Logger adapterLogger() {
        return (Logger) LoggerFactory.getLogger(EmployeeServicePersistenceAdapter.class);
    }

    // ── the fix this task exists for: orphaned service_id must not surface ──

    @Test
    void findByEmployeeId_orphanedAssignment_isDroppedFromTheResult() {
        EmployeeServiceJpaEntity liveAssignment = employeeServiceEntity(LIVE_SERVICE_ID);
        EmployeeServiceJpaEntity orphanedAssignment = employeeServiceEntity(ORPHANED_SERVICE_ID);
        when(repository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of(liveAssignment, orphanedAssignment));

        ServiceOfferingJpaEntity liveService = serviceEntity(LIVE_SERVICE_ID, "svc_haircut", "Haircut");
        // findAllById only returns the service that still exists; the orphaned id is simply absent
        when(serviceRepository.findAllById(List.of(LIVE_SERVICE_ID, ORPHANED_SERVICE_ID)))
                .thenReturn(List.of(liveService));

        List<EmployeeServiceAssignment> result = adapter.findByEmployeeId(EMPLOYEE_ID);

        assertThat(result)
                .as("the orphaned row (service_id 999 no longer exists) must not reach the caller")
                .hasSize(1);
        assertThat(result.get(0).getServiceId()).isEqualTo(LIVE_SERVICE_ID);
        assertThat(result.get(0).getServiceExternalId()).isEqualTo("svc_haircut");
    }

    @Test
    void findByEmployeeId_orphanedAssignment_neverProducesANullServiceExternalId() {
        // This is exactly the bug the public payload leaked: a null landing in EmployeePublicResponse.serviceIds.
        EmployeeServiceJpaEntity orphanedAssignment = employeeServiceEntity(ORPHANED_SERVICE_ID);
        when(repository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of(orphanedAssignment));
        when(serviceRepository.findAllById(List.of(ORPHANED_SERVICE_ID))).thenReturn(List.of());

        List<EmployeeServiceAssignment> result = adapter.findByEmployeeId(EMPLOYEE_ID);

        assertThat(result).isEmpty();
        assertThat(result).noneMatch(a -> a.getServiceExternalId() == null);
    }

    @Test
    void findByEmployeeId_orphanedAssignment_logsAWarningWithBothIds() {
        EmployeeServiceJpaEntity orphanedAssignment = employeeServiceEntity(ORPHANED_SERVICE_ID);
        when(repository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of(orphanedAssignment));
        when(serviceRepository.findAllById(List.of(ORPHANED_SERVICE_ID))).thenReturn(List.of());

        adapter.findByEmployeeId(EMPLOYEE_ID);

        assertThat(logAppender.list)
                .as("an orphaned row is a data-corruption symptom and must not be swallowed silently")
                .anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
        assertThat(logAppender.list).noneMatch(event -> event.getLevel() == Level.ERROR);
        assertThat(logAppender.list)
                .anySatisfy(event -> {
                    assertThat(hasKeyValue(event, "employeeId", EMPLOYEE_ID)).isTrue();
                    assertThat(hasKeyValue(event, "serviceId", ORPHANED_SERVICE_ID)).isTrue();
                });
    }

    @Test
    void findByEmployeeId_noOrphans_doesNotLogAnything() {
        EmployeeServiceJpaEntity liveAssignment = employeeServiceEntity(LIVE_SERVICE_ID);
        when(repository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of(liveAssignment));
        when(serviceRepository.findAllById(List.of(LIVE_SERVICE_ID)))
                .thenReturn(List.of(serviceEntity(LIVE_SERVICE_ID, "svc_haircut", "Haircut")));

        adapter.findByEmployeeId(EMPLOYEE_ID);

        assertThat(logAppender.list).isEmpty();
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static boolean hasKeyValue(ILoggingEvent event, String key, Object value) {
        return event.getKeyValuePairs() != null && event.getKeyValuePairs().stream()
                .anyMatch(kvp -> key.equals(kvp.key) && Objects.equals(String.valueOf(value), String.valueOf(kvp.value)));
    }

    private static EmployeeServiceJpaEntity employeeServiceEntity(Long serviceId) {
        return EmployeeServiceJpaEntity.builder()
                .employeeId(EMPLOYEE_ID)
                .serviceId(serviceId)
                .tenantId("sal_tenant-A")
                .build();
    }

    private static ServiceOfferingJpaEntity serviceEntity(Long id, String externalId, String name) {
        ServiceOfferingJpaEntity entity = new ServiceOfferingJpaEntity();
        entity.setId(id);
        entity.setExternalId(externalId);
        entity.setName(name);
        entity.setDurationMinutes(30);
        entity.setPrice(new BigDecimal("25.00"));
        return entity;
    }
}
