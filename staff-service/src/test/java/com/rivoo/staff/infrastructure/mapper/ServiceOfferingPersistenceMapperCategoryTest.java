package com.rivoo.staff.infrastructure.mapper;

import com.rivoo.staff.domain.model.ServiceOffering;
import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.ServiceOfferingJpaEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the {@code category} mapping in BOTH directions of the persistence mapper.
 * <p>
 * This project does not set {@code unmappedTargetPolicy = ERROR} on MapStruct, so the default
 * {@code WARN} applies: a forgotten mapping does NOT break the build, it silently leaves the
 * target field at its type default. A dropped {@code category} would therefore only surface as
 * data quietly vanishing on write or read. This test makes that failure mode loud.
 */
class ServiceOfferingPersistenceMapperCategoryTest {

    private final ServiceOfferingPersistenceMapper mapper = new ServiceOfferingPersistenceMapperImpl();

    @Test
    void toJpaEntity_carriesTheCategory() {
        ServiceOffering domain = ServiceOffering.builder()
                .id(1L)
                .externalId("svc_haircut")
                .tenantId("sal_tenant-A")
                .name("Corte caballero")
                .description("Corte y peinado")
                .durationMinutes(30)
                .price(new BigDecimal("15.00"))
                .currency("EUR")
                .category("Cortes")
                .active(true)
                .build();

        ServiceOfferingJpaEntity entity = mapper.toJpaEntity(domain);

        assertThat(entity.getCategory()).isEqualTo("Cortes");
    }

    @Test
    void toJpaEntity_withoutCategory_leavesItNull() {
        ServiceOffering domain = ServiceOffering.builder()
                .externalId("svc_haircut")
                .name("Corte caballero")
                .durationMinutes(30)
                .price(new BigDecimal("15.00"))
                .currency("EUR")
                .active(true)
                .build();

        ServiceOfferingJpaEntity entity = mapper.toJpaEntity(domain);

        assertThat(entity.getCategory()).isNull();
    }

    @Test
    void toDomain_carriesTheCategory() {
        ServiceOfferingJpaEntity entity = new ServiceOfferingJpaEntity();
        entity.setId(1L);
        entity.setExternalId("svc_haircut");
        entity.setName("Corte caballero");
        entity.setDescription("Corte y peinado");
        entity.setDurationMinutes(30);
        entity.setPrice(new BigDecimal("15.00"));
        entity.setCurrency("EUR");
        entity.setCategory("Barba");
        entity.setActive(true);

        ServiceOffering domain = mapper.toDomain(entity);

        assertThat(domain.getCategory()).isEqualTo("Barba");
    }

    @Test
    void toDomain_withoutCategory_leavesItNull() {
        ServiceOfferingJpaEntity entity = new ServiceOfferingJpaEntity();
        entity.setExternalId("svc_haircut");
        entity.setName("Corte caballero");
        entity.setDurationMinutes(30);
        entity.setPrice(new BigDecimal("15.00"));
        entity.setCurrency("EUR");
        entity.setActive(true);

        ServiceOffering domain = mapper.toDomain(entity);

        assertThat(domain.getCategory()).isNull();
    }

    @Test
    void roundTrip_domainToEntityToDomain_preservesTheCategory() {
        ServiceOffering original = ServiceOffering.builder()
                .externalId("svc_haircut")
                .tenantId("sal_tenant-A")
                .name("Corte caballero")
                .durationMinutes(30)
                .price(new BigDecimal("15.00"))
                .currency("EUR")
                .category("Color")
                .active(true)
                .build();

        ServiceOffering roundTripped = mapper.toDomain(mapper.toJpaEntity(original));

        assertThat(roundTripped.getCategory()).isEqualTo("Color");
    }
}
