package com.rivoo.salon.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rivoo.salon.application.dto.BusinessHoursResponse;
import com.rivoo.salon.application.dto.SalonPublicResponse;
import com.rivoo.salon.domain.exception.SalonNotFoundException;
import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonBusinessHours;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.port.out.BusinessHoursPersistencePort;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import com.rivoo.salon.domain.port.out.StaffServicePort;
import com.rivoo.salon.infrastructure.mapper.SalonDtoMapper;
import com.rivoo.salon.infrastructure.mapper.SalonDtoMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    private SalonService salonService;

    @BeforeEach
    void setUp() {
        // Real mapper (not mocked) so we exercise the actual isOpen/open MapStruct mapping.
        SalonDtoMapper mapper = new SalonDtoMapperImpl();
        salonService = new SalonService(salonPersistencePort, businessHoursPersistencePort, staffServicePort, mapper);
    }

    // ── the aggregate this task exists for ────────────────────────────────

    @Test
    void getPublicBySlug_aggregatesHoursServicesAndEmployees() {
        Salon salon = activeSalon();
        when(salonPersistencePort.findBySlug(SLUG)).thenReturn(Optional.of(salon));
        when(businessHoursPersistencePort.findBySalonId(salon.getId())).thenReturn(List.of(
                SalonBusinessHours.builder()
                        .id(1L).salonId(salon.getId()).dayOfWeek(1).open(true)
                        .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(18, 0))
                        .build()
        ));
        when(staffServicePort.getPublicServices(TENANT_ID)).thenReturn(List.of(
                new StaffServicePort.ServicePublicInfo("svc_1", "Haircut", "Basic haircut", 30,
                        new BigDecimal("25.00"), "EUR")
        ));
        when(staffServicePort.getPublicEmployees(TENANT_ID)).thenReturn(List.of(
                new StaffServicePort.EmployeePublicInfo("emp_1", "Ana", "Lopez", "Stylist", List.of("svc_1"))
        ));

        SalonPublicResponse response = salonService.getPublicBySlug(SLUG);

        assertThat(response.name()).isEqualTo("Demo Salon");
        assertThat(response.slug()).isEqualTo(SLUG);

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

    // ── only ACTIVE salons are publicly bookable ──────────────────────────

    @ParameterizedTest
    @EnumSource(value = SalonStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void getPublicBySlug_nonActiveSalon_throwsSalonNotFound(SalonStatus status) {
        Salon salon = activeSalon();
        salon.setStatus(status);
        when(salonPersistencePort.findBySlug(SLUG)).thenReturn(Optional.of(salon));

        assertThatThrownBy(() -> salonService.getPublicBySlug(SLUG))
                .isInstanceOf(SalonNotFoundException.class);

        verifyNoInteractions(businessHoursPersistencePort);
        verifyNoInteractions(staffServicePort);
    }

    @Test
    void getPublicBySlug_activeSalon_doesNotThrow() {
        Salon salon = activeSalon();
        when(salonPersistencePort.findBySlug(SLUG)).thenReturn(Optional.of(salon));
        when(businessHoursPersistencePort.findBySalonId(salon.getId())).thenReturn(List.of());
        when(staffServicePort.getPublicServices(TENANT_ID)).thenReturn(List.of());
        when(staffServicePort.getPublicEmployees(TENANT_ID)).thenReturn(List.of());

        assertThatCode(() -> salonService.getPublicBySlug(SLUG)).doesNotThrowAnyException();
    }

    // ── staff-service degraded: the page still loads ──────────────────────

    @Test
    void getPublicBySlug_staffServiceReturnsEmptyLists_buildsAggregateWithoutError() {
        Salon salon = activeSalon();
        when(salonPersistencePort.findBySlug(SLUG)).thenReturn(Optional.of(salon));
        when(businessHoursPersistencePort.findBySalonId(salon.getId())).thenReturn(List.of());
        when(staffServicePort.getPublicServices(TENANT_ID)).thenReturn(List.of());
        when(staffServicePort.getPublicEmployees(TENANT_ID)).thenReturn(List.of());

        SalonPublicResponse response = salonService.getPublicBySlug(SLUG);

        assertThat(response.services()).isEmpty();
        assertThat(response.employees()).isEmpty();
        assertThat(response.businessHours()).isEmpty();
    }

    // ── regression test for the isOpen/open Jackson bug ───────────────────

    @Test
    void businessHoursResponse_serializesIsOpenField_notOpen() throws Exception {
        BusinessHoursResponse response = new BusinessHoursResponse(1, true, null, null, null, null);

        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).contains("\"isOpen\"");
        assertThat(json).doesNotContain("\"open\":");
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
