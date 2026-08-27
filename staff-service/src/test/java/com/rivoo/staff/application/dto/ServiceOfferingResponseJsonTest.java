package com.rivoo.staff.application.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the active/isActive Jackson bug (Block 2 of the wizard-services-empty
 * investigation): the internal "wizard interno de citas" filtered on {@code isActive}, but this
 * DTO used to emit {@code active}, so the filter always dropped every service. Sibling of
 * {@code WorkingHoursResponseJsonTest} for this DTO.
 * <p>
 * {@link ServiceOfferingResponse} is a bare record with no Jackson annotations — verified with
 * {@code grep -rn "JsonProperty" staff-service/src/main}, which returns nothing — and there is no
 * {@code PropertyNamingStrategy} anywhere in this module or in {@code rivoo-common} — verified with
 * {@code grep -rln "PropertyNamingStrategy" staff-service/src/main rivoo-common/src/main}, which
 * also returns nothing — so the record component name ({@code isActive}) is exactly what Jackson 3
 * uses as the JSON property name.
 */
@JsonTest
class ServiceOfferingResponseJsonTest {

    @Autowired
    private JacksonTester<ServiceOfferingResponse> json;

    @Test
    void serializesIsActiveField_notActive() throws Exception {
        ServiceOfferingResponse response = new ServiceOfferingResponse(
                "svc_123", "Corte caballero", "Corte y peinado", 30,
                new BigDecimal("15.00"), "EUR", null, true, Instant.now(), Instant.now());

        String jsonContent = json.write(response).getJson();

        assertThat(jsonContent).contains("\"isActive\"");
        assertThat(jsonContent).doesNotContain("\"active\":");
    }

    /**
     * Contract with the frontend: {@code rivoo-frontend/src/types/service.ts} declares
     * {@code category: string | null} on {@code ServiceOffering} and renders it in
     * {@code service-card.tsx} and the appointment wizard, so the JSON key must be exactly
     * {@code category}.
     */
    @Test
    void serializesCategoryUnderTheKeyTheFrontendReads() throws Exception {
        ServiceOfferingResponse response = new ServiceOfferingResponse(
                "svc_123", "Corte caballero", "Corte y peinado", 30,
                new BigDecimal("15.00"), "EUR", "Cortes", true, Instant.now(), Instant.now());

        String jsonContent = json.write(response).getJson();

        assertThat(jsonContent).contains("\"category\":\"Cortes\"");
    }
}
