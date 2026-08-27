package com.rivoo.staff.application;

import com.rivoo.staff.application.dto.ServiceOfferingInternalResponse;
import com.rivoo.staff.application.dto.ServiceOfferingPublicResponse;
import com.rivoo.staff.domain.exception.ServiceOfferingNotFoundException;
import com.rivoo.staff.domain.model.ServiceOffering;
import com.rivoo.staff.domain.port.out.ServiceOfferingPersistencePort;
import com.rivoo.staff.infrastructure.mapper.ServiceOfferingDtoMapper;
import com.rivoo.staff.infrastructure.mapper.ServiceOfferingDtoMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceOfferingPublicListTest {

    @Mock
    private ServiceOfferingPersistencePort serviceOfferingPersistencePort;

    private ServiceOfferingService serviceOfferingService;

    private static final String TENANT_A = "sal_tenant-A";
    private static final String TENANT_B = "sal_tenant-B";

    @BeforeEach
    void setUp() {
        ServiceOfferingDtoMapper mapper = new ServiceOfferingDtoMapperImpl();
        serviceOfferingService = new ServiceOfferingService(serviceOfferingPersistencePort, mapper);
    }

    // ── the fix this task exists for: explicit tenant filtering ──────────

    @Test
    void listPublicByTenant_callsExplicitTenantFilteredQuery_neverTheUnfilteredOne() {
        when(serviceOfferingPersistencePort.findAllActiveByTenantId(TENANT_A)).thenReturn(List.of());

        serviceOfferingService.listPublicByTenant(TENANT_A);

        verify(serviceOfferingPersistencePort).findAllActiveByTenantId(TENANT_A);
        verify(serviceOfferingPersistencePort, never()).findAllActive(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listPublicByTenant_happyPath_mapsExternalIdAsId() {
        ServiceOffering service = ServiceOffering.builder()
                .id(1L)
                .externalId("svc_haircut")
                .tenantId(TENANT_A)
                .name("Haircut")
                .description("Classic haircut")
                .durationMinutes(30)
                .price(new BigDecimal("25.00"))
                .currency("EUR")
                .active(true)
                .build();
        when(serviceOfferingPersistencePort.findAllActiveByTenantId(TENANT_A)).thenReturn(List.of(service));

        List<ServiceOfferingPublicResponse> result = serviceOfferingService.listPublicByTenant(TENANT_A);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("svc_haircut");
        assertThat(result.get(0).name()).isEqualTo("Haircut");
    }

    // ── guard against a missing/blank tenant (anonymous visitor with no context) ──

    @Test
    void listPublicByTenant_blankTenantId_throwsIllegalArgumentException_withoutTouchingPersistence() {
        assertThatThrownBy(() -> serviceOfferingService.listPublicByTenant(""))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(serviceOfferingPersistencePort);
    }

    @Test
    void listPublicByTenant_nullTenantId_throwsIllegalArgumentException_withoutTouchingPersistence() {
        assertThatThrownBy(() -> serviceOfferingService.listPublicByTenant(null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(serviceOfferingPersistencePort);
    }

    // ── getInternal: cross-tenant regression (block 1) ────────────────────

    @Test
    void getInternal_serviceBelongsToDifferentTenant_throwsNotFound_neverExposesIt() {
        ServiceOffering serviceOfTenantB = buildService("svc_from_b", TENANT_B);
        when(serviceOfferingPersistencePort.findByExternalId("svc_from_b")).thenReturn(java.util.Optional.of(serviceOfTenantB));

        assertThatThrownBy(() -> serviceOfferingService.getInternal(TENANT_A, "svc_from_b"))
                .isInstanceOf(ServiceOfferingNotFoundException.class);
    }

    @Test
    void getInternal_sameTenant_returnsResponse() {
        ServiceOffering serviceOfTenantA = buildService("svc_from_a", TENANT_A);
        when(serviceOfferingPersistencePort.findByExternalId("svc_from_a")).thenReturn(java.util.Optional.of(serviceOfTenantA));

        ServiceOfferingInternalResponse response = serviceOfferingService.getInternal(TENANT_A, "svc_from_a");

        assertThat(response.id()).isEqualTo("svc_from_a");
    }

    @Test
    void getInternal_blankTenantId_throwsIllegalArgumentException_withoutTouchingPersistence() {
        assertThatThrownBy(() -> serviceOfferingService.getInternal("", "svc_from_a"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(serviceOfferingPersistencePort);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private ServiceOffering buildService(String externalId, String tenantId) {
        return ServiceOffering.builder()
                .id(1L)
                .externalId(externalId)
                .tenantId(tenantId)
                .name("Haircut")
                .description("Classic haircut")
                .durationMinutes(30)
                .price(new BigDecimal("25.00"))
                .currency("EUR")
                .active(true)
                .build();
    }
}
