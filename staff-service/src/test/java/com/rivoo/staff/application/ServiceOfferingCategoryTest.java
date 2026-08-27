package com.rivoo.staff.application;

import com.rivoo.staff.application.dto.CreateServiceOfferingRequest;
import com.rivoo.staff.application.dto.ServiceOfferingResponse;
import com.rivoo.staff.application.dto.UpdateServiceOfferingRequest;
import com.rivoo.staff.domain.model.ServiceOffering;
import com.rivoo.staff.domain.port.out.ServiceOfferingPersistencePort;
import com.rivoo.staff.infrastructure.mapper.ServiceOfferingDtoMapper;
import com.rivoo.staff.infrastructure.mapper.ServiceOfferingDtoMapperImpl;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the {@code category} field on service offerings: the frontend has shipped a full category
 * feature ({@code rivoo-frontend/src/types/service.ts}, {@code service-form.tsx}) whose value the
 * backend used to discard silently, because no such field existed in the DTOs, the domain model or
 * the {@code services} table.
 * <p>
 * Scope is deliberately the AUTHENTICATED surface only: {@code ServiceOfferingPublicResponse} and
 * {@code ServiceOfferingInternalResponse} do not carry a category, matching the frontend
 * {@code ServicePublic} type and appointment-service {@code ServiceOfferingInternalDto}.
 */
@ExtendWith(MockitoExtension.class)
class ServiceOfferingCategoryTest {

    private static final String TENANT_A = "sal_tenant-A";

    private static ValidatorFactory factory;
    private static Validator validator;

    @Mock
    private ServiceOfferingPersistencePort serviceOfferingPersistencePort;

    private ServiceOfferingService serviceOfferingService;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        factory.close();
    }

    @BeforeEach
    void setUp() {
        ServiceOfferingDtoMapper mapper = new ServiceOfferingDtoMapperImpl();
        serviceOfferingService = new ServiceOfferingService(serviceOfferingPersistencePort, mapper);
    }

    // -- create ----------------------------------------------------------

    @Test
    void create_withCategory_storesItOnTheDomainModelAndReturnsIt() {
        when(serviceOfferingPersistencePort.existsByNameAndTenantId("Corte caballero", TENANT_A)).thenReturn(false);
        when(serviceOfferingPersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceOfferingResponse response = serviceOfferingService.create(TENANT_A,
                new CreateServiceOfferingRequest("Corte caballero", "Corte y peinado", 30,
                        new BigDecimal("15.00"), "EUR", "Cortes"));

        ArgumentCaptor<ServiceOffering> captor = ArgumentCaptor.forClass(ServiceOffering.class);
        verify(serviceOfferingPersistencePort).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo("Cortes");
        assertThat(response.category()).isEqualTo("Cortes");
    }

    @Test
    void create_withoutCategory_leavesItNull_withoutCrashing() {
        when(serviceOfferingPersistencePort.existsByNameAndTenantId("Corte caballero", TENANT_A)).thenReturn(false);
        when(serviceOfferingPersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceOfferingResponse response = serviceOfferingService.create(TENANT_A,
                new CreateServiceOfferingRequest("Corte caballero", "Corte y peinado", 30,
                        new BigDecimal("15.00"), "EUR", null));

        ArgumentCaptor<ServiceOffering> captor = ArgumentCaptor.forClass(ServiceOffering.class);
        verify(serviceOfferingPersistencePort).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isNull();
        assertThat(response.category()).isNull();
    }

    // -- update ----------------------------------------------------------

    @Test
    void update_withNewCategory_replacesTheExistingOne() {
        when(serviceOfferingPersistencePort.findByExternalId("svc_haircut"))
                .thenReturn(Optional.of(existingService("Cortes")));
        when(serviceOfferingPersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceOfferingResponse response = serviceOfferingService.update(TENANT_A, "svc_haircut",
                new UpdateServiceOfferingRequest(null, null, null, null, null, "Barba"));

        assertThat(response.category()).isEqualTo("Barba");
    }

    @Test
    void update_onAServiceWithoutCategory_setsIt() {
        when(serviceOfferingPersistencePort.findByExternalId("svc_haircut"))
                .thenReturn(Optional.of(existingService(null)));
        when(serviceOfferingPersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceOfferingResponse response = serviceOfferingService.update(TENANT_A, "svc_haircut",
                new UpdateServiceOfferingRequest(null, null, null, null, null, "Color"));

        assertThat(response.category()).isEqualTo("Color");
    }

    /**
     * Documents a PREEXISTING limitation shared by every optional field on this entity
     * ({@code description}, {@code currency}, ...): the update use case uses the
     * {@code if (request.x() != null)} idiom, so a null in the payload means "leave untouched",
     * not "clear". A category therefore cannot be reset to null through PUT. Kept deliberately
     * consistent rather than inventing different semantics for this one field.
     */
    @Test
    void update_withNullCategory_leavesTheExistingCategoryUntouched() {
        when(serviceOfferingPersistencePort.findByExternalId("svc_haircut"))
                .thenReturn(Optional.of(existingService("Cortes")));
        when(serviceOfferingPersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceOfferingResponse response = serviceOfferingService.update(TENANT_A, "svc_haircut",
                new UpdateServiceOfferingRequest(null, null, null, null, null, null));

        assertThat(response.category()).isEqualTo("Cortes");
    }

    // -- validation: @Size(max = 100) matches the VARCHAR(100) column -----

    @Test
    void createRequest_rejectsCategoryLongerThanTheColumn() {
        Set<ConstraintViolation<CreateServiceOfferingRequest>> violations = validator.validate(
                new CreateServiceOfferingRequest("Corte caballero", null, 30,
                        new BigDecimal("15.00"), "EUR", "C".repeat(101)));

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("category"));
    }

    @Test
    void createRequest_acceptsCategoryOfExactlyTheColumnWidth() {
        Set<ConstraintViolation<CreateServiceOfferingRequest>> violations = validator.validate(
                new CreateServiceOfferingRequest("Corte caballero", null, 30,
                        new BigDecimal("15.00"), "EUR", "C".repeat(100)));

        assertThat(violations).isEmpty();
    }

    @Test
    void updateRequest_rejectsCategoryLongerThanTheColumn() {
        Set<ConstraintViolation<UpdateServiceOfferingRequest>> violations = validator.validate(
                new UpdateServiceOfferingRequest(null, null, null, null, null, "C".repeat(101)));

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("category"));
    }

    @Test
    void updateRequest_acceptsNullCategory() {
        Set<ConstraintViolation<UpdateServiceOfferingRequest>> violations = validator.validate(
                new UpdateServiceOfferingRequest(null, null, null, null, null, null));

        assertThat(violations).isEmpty();
    }

    // -- helpers ---------------------------------------------------------

    private ServiceOffering existingService(String category) {
        return ServiceOffering.builder()
                .id(1L)
                .externalId("svc_haircut")
                .tenantId(TENANT_A)
                .name("Corte caballero")
                .description("Corte y peinado")
                .durationMinutes(30)
                .price(new BigDecimal("15.00"))
                .currency("EUR")
                .category(category)
                .active(true)
                .build();
    }
}
