package com.rivoo.salon.application;

import com.rivoo.salon.application.dto.SalonPublicResponse;
import com.rivoo.salon.domain.exception.SalonNotFoundException;
import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonBusinessHours;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.port.out.BusinessHoursPersistencePort;
import com.rivoo.salon.domain.port.out.NotificationServicePort;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import com.rivoo.salon.domain.port.out.StaffServicePort;
import com.rivoo.salon.infrastructure.mapper.SalonDtoMapper;
import com.rivoo.salon.infrastructure.mapper.SalonDtoMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@code SalonService.getPublicBySlug} aggregate itself
 * (does it combine salon + hours + services + employees correctly, does it
 * degrade gracefully). {@code SalonPublicSnapshotLoader} is mocked here: its
 * own ACTIVE-only filtering behavior is covered by
 * {@link SalonPublicSnapshotLoaderTest}, and the transaction-boundary
 * guarantee (staff-service calls happen after the DB transaction is closed)
 * is covered by {@link SalonServiceTransactionBoundaryTest}, which needs a
 * real Spring transactional proxy and therefore cannot be proven with plain
 * Mockito mocks.
 */
@ExtendWith(MockitoExtension.class)
class SalonServicePublicAggregateTest {

    private static final String SLUG = "salon-demo";
    private static final String TENANT_ID = "sal_demo";

    @Mock
    private SalonPersistencePort salonPersistencePort;

    @Mock
    private BusinessHoursPersistencePort businessHoursPersistencePort;

    @Mock
    private StaffServicePort staffServicePort;

    @Mock
    private SalonPublicSnapshotLoader salonPublicSnapshotLoader;

    @Mock
    private NotificationServicePort notificationServicePort;

    private SalonService salonService;

    @BeforeEach
    void setUp() {
        // Real mapper (not mocked) so we exercise the actual isOpen/open MapStruct mapping.
        SalonDtoMapper mapper = new SalonDtoMapperImpl();
        salonService = new SalonService(salonPersistencePort, businessHoursPersistencePort, staffServicePort,
                mapper, salonPublicSnapshotLoader, notificationServicePort);
    }

    // ── the aggregate this task exists for ────────────────────────────────

    @Test
    void getPublicBySlug_aggregatesHoursServicesAndEmployees() {
        Salon salon = activeSalon();
        List<SalonBusinessHours> hours = List.of(
                SalonBusinessHours.builder()
                        .id(1L).salonId(salon.getId()).dayOfWeek(1).open(true)
                        .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(18, 0))
                        .build()
        );
        when(salonPublicSnapshotLoader.loadActiveSalon(SLUG)).thenReturn(new SalonPublicSnapshot(salon, hours));
        when(staffServicePort.getPublicServices(TENANT_ID)).thenReturn(Optional.of(List.of(
                new StaffServicePort.ServicePublicInfo("svc_1", "Haircut", "Basic haircut", 30,
                        new BigDecimal("25.00"), "EUR")
        )));
        when(staffServicePort.getPublicEmployees(TENANT_ID)).thenReturn(Optional.of(List.of(
                new StaffServicePort.EmployeePublicInfo("emp_1", "Ana", "Lopez", "Stylist", List.of("svc_1"))
        )));

        SalonPublicResponse response = salonService.getPublicBySlug(SLUG);

        assertThat(response.name()).isEqualTo("Demo Salon");
        assertThat(response.slug()).isEqualTo(SLUG);
        assertThat(response.servicesUnavailable())
                .as("the services call succeeded, it must not be reported as unavailable")
                .isFalse();
        assertThat(response.employeesUnavailable())
                .as("the employees call succeeded, it must not be reported as unavailable")
                .isFalse();

        assertThat(response.businessHours()).hasSize(1);
        assertThat(response.businessHours().get(0).dayOfWeek()).isEqualTo(1);
        assertThat(response.businessHours().get(0).isOpen()).isTrue();
        assertThat(response.businessHours().get(0).openTime()).isEqualTo(LocalTime.of(9, 0));

        assertThat(response.services()).hasSize(1);
        assertThat(response.services().get(0).id()).isEqualTo("svc_1");
        assertThat(response.services().get(0).name()).isEqualTo("Haircut");
        assertThat(response.services().get(0).durationMinutes()).isEqualTo(30);
        assertThat(response.services().get(0).price()).isEqualByComparingTo(new BigDecimal("25.00"));

        assertThat(response.employees()).hasSize(1);
        assertThat(response.employees().get(0).id()).isEqualTo("emp_1");
        assertThat(response.employees().get(0).firstName()).isEqualTo("Ana");
        assertThat(response.employees().get(0).serviceIds()).containsExactly("svc_1");

        verify(staffServicePort).getPublicServices(TENANT_ID);
        verify(staffServicePort).getPublicEmployees(TENANT_ID);
    }

    // ── a missing/non-bookable salon short-circuits before calling staff-service ──

    @Test
    void getPublicBySlug_snapshotLoaderThrows_propagatesAndSkipsStaffService() {
        when(salonPublicSnapshotLoader.loadActiveSalon(SLUG)).thenThrow(new SalonNotFoundException(SLUG));

        assertThatThrownBy(() -> salonService.getPublicBySlug(SLUG))
                .isInstanceOf(SalonNotFoundException.class);

        verifyNoInteractions(staffServicePort);
    }

    @Test
    void getPublicBySlug_activeSalon_doesNotThrow() {
        Salon salon = activeSalon();
        when(salonPublicSnapshotLoader.loadActiveSalon(SLUG)).thenReturn(new SalonPublicSnapshot(salon, List.of()));
        when(staffServicePort.getPublicServices(TENANT_ID)).thenReturn(Optional.of(List.of()));
        when(staffServicePort.getPublicEmployees(TENANT_ID)).thenReturn(Optional.of(List.of()));

        assertThatCode(() -> salonService.getPublicBySlug(SLUG)).doesNotThrowAnyException();
    }

    // ── legitimately empty catalogue vs. staff-service failure ────────────

    @Test
    void getPublicBySlug_staffServiceRespondsWithEmptyLists_neitherFlagIsUnavailable() {
        // A salon that skipped the optional employees/services onboarding step: both
        // calls succeed (Optional.of(...)) and simply carry empty lists. This is the
        // test that gives the task its purpose: it must be indistinguishable from a
        // real failure only in the list contents, never in either "unavailable" flag.
        Salon salon = activeSalon();
        when(salonPublicSnapshotLoader.loadActiveSalon(SLUG)).thenReturn(new SalonPublicSnapshot(salon, List.of()));
        when(staffServicePort.getPublicServices(TENANT_ID)).thenReturn(Optional.of(List.of()));
        when(staffServicePort.getPublicEmployees(TENANT_ID)).thenReturn(Optional.of(List.of()));

        SalonPublicResponse response = salonService.getPublicBySlug(SLUG);

        assertThat(response.services()).isEmpty();
        assertThat(response.employees()).isEmpty();
        assertThat(response.businessHours()).isEmpty();
        assertThat(response.servicesUnavailable())
                .as("empty services by onboarding choice is a legitimate state, not a staff-service failure")
                .isFalse();
        assertThat(response.employeesUnavailable())
                .as("empty employees by onboarding choice is a legitimate state, not a staff-service failure")
                .isFalse();
    }

    @Test
    void getPublicBySlug_servicesCallFails_onlyServicesUnavailableAndEmployeesStillArrive() {
        Salon salon = activeSalon();
        when(salonPublicSnapshotLoader.loadActiveSalon(SLUG)).thenReturn(new SalonPublicSnapshot(salon, List.of()));
        when(staffServicePort.getPublicServices(TENANT_ID)).thenReturn(Optional.empty());
        when(staffServicePort.getPublicEmployees(TENANT_ID)).thenReturn(Optional.of(List.of(
                new StaffServicePort.EmployeePublicInfo("emp_1", "Ana", "Lopez", "Stylist", List.of("svc_1"))
        )));

        SalonPublicResponse response = salonService.getPublicBySlug(SLUG);

        assertThat(response.servicesUnavailable())
                .as("the services call failed, it must be flagged as unavailable")
                .isTrue();
        assertThat(response.employeesUnavailable())
                .as("the employees call succeeded on its own, it must stay available")
                .isFalse();
        assertThat(response.services()).isEmpty();
        assertThat(response.employees())
                .as("a failure on the services call must not empty out the employees that DID load successfully")
                .hasSize(1);
    }

    @Test
    void getPublicBySlug_employeesCallFails_onlyEmployeesUnavailableAndServicesStillArriveWithRealData() {
        // Mirror of getPublicBySlug_servicesCallFails_onlyServicesUnavailableAndEmployeesStillArrive.
        // This is the partial-failure case the whole split exists for: before the
        // split, `catalogueUnavailable = servicesResult.isEmpty() || employeesResult.isEmpty()`
        // would report `true` here even though `services` below carries real,
        // non-empty data — a partial failure reported as if the whole catalogue
        // were gone.
        Salon salon = activeSalon();
        when(salonPublicSnapshotLoader.loadActiveSalon(SLUG)).thenReturn(new SalonPublicSnapshot(salon, List.of()));
        when(staffServicePort.getPublicServices(TENANT_ID)).thenReturn(Optional.of(List.of(
                new StaffServicePort.ServicePublicInfo("svc_1", "Haircut", "Basic haircut", 30,
                        new BigDecimal("25.00"), "EUR")
        )));
        when(staffServicePort.getPublicEmployees(TENANT_ID)).thenReturn(Optional.empty());

        SalonPublicResponse response = salonService.getPublicBySlug(SLUG);

        assertThat(response.employeesUnavailable())
                .as("the employees call failed, it must be flagged as unavailable")
                .isTrue();
        assertThat(response.servicesUnavailable())
                .as("the services call succeeded on its own, it must stay available")
                .isFalse();
        assertThat(response.employees()).isEmpty();
        assertThat(response.services())
                .as("a failure on the employees call must not empty out the services that DID load successfully")
                .hasSize(1);
        assertThat(response.services().get(0).id()).isEqualTo("svc_1");
        assertThat(response.services().get(0).name()).isEqualTo("Haircut");
    }

    @Test
    void getPublicBySlug_bothStaffServiceCallsFail_bothFlagsUnavailableWithEmptyLists() {
        Salon salon = activeSalon();
        when(salonPublicSnapshotLoader.loadActiveSalon(SLUG)).thenReturn(new SalonPublicSnapshot(salon, List.of()));
        when(staffServicePort.getPublicServices(TENANT_ID)).thenReturn(Optional.empty());
        when(staffServicePort.getPublicEmployees(TENANT_ID)).thenReturn(Optional.empty());

        SalonPublicResponse response = salonService.getPublicBySlug(SLUG);

        assertThat(response.servicesUnavailable()).isTrue();
        assertThat(response.employeesUnavailable()).isTrue();
        assertThat(response.services()).isEmpty();
        assertThat(response.employees()).isEmpty();
    }

    // ── helpers ────────────────────────────────────────────────────────

    private Salon activeSalon() {
        return Salon.builder()
                .id(1L)
                .externalId(TENANT_ID)
                .tenantId(TENANT_ID)
                .name("Demo Salon")
                .slug(SLUG)
                .phone("+34600000000")
                .status(SalonStatus.ACTIVE)
                .build();
    }
}
