package com.rivoo.salon.application.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the wire names of the "was this list reachable" flags on
 * {@link SalonPublicResponse}, run against the actual serializer used in
 * production ({@code @JsonTest} boots the Boot-autoconfigured
 * {@link JacksonTester}, backed by Jackson 3's
 * {@code tools.jackson.databind.json.JsonMapper} — see
 * {@code BusinessHoursResponseJsonTest} for how that was verified to be the
 * real HTTP-response serializer in this module).
 * <p>
 * {@link SalonPublicResponse} is a bare record with no Jackson annotations
 * ({@code grep -rn "JsonProperty" salon-service/src/main} returns nothing),
 * so each wire name is exactly its record component name. Unlike
 * {@code isOpen}/{@code open} in {@code BusinessHoursResponse}, the flags
 * tested here do not start with an {@code is}/{@code get} prefix, so they are
 * not exposed to that specific bean-introspection stripping bug — this test
 * exists as a regression lock on the field names themselves (this DTO has no
 * consumer in this repo yet per {@code rivoo-frontend/src/types/salon.ts},
 * so a silent rename here would only surface once the frontend starts
 * reading it).
 * <p>
 * There are deliberately two independent flags, not one: they were split
 * from a single {@code catalogueUnavailable} field because it was an OR of
 * two independent staff-service calls, so it could be {@code true} while the
 * {@code services} array in the very same payload carried real data — a
 * partial failure reported as a total one. {@code servicesUnavailable} and
 * {@code employeesUnavailable} each map 1:1 to the reservation flow's
 * {@code public-service-step} and {@code public-employee-step}, so a partial
 * failure can be represented as partial.
 */
@JsonTest
class SalonPublicResponseJsonTest {

    @Autowired
    private JacksonTester<SalonPublicResponse> json;

    @Test
    void serializesServicesAndEmployeesUnavailableFieldsIndependently() throws Exception {
        SalonPublicResponse response = new SalonPublicResponse(
                "Demo Salon", "salon-demo", "+34600000000", "A demo salon", null, null,
                "Carrer Demo 1", "Barcelona", "08001",
                List.of(),
                List.of(new ServicePublicResponse("svc_1", "Haircut", "Basic haircut", 30,
                        new BigDecimal("25.00"), "EUR")),
                List.of(new EmployeePublicResponse("emp_1", "Ana", "Lopez", "Stylist", List.of("svc_1"))),
                false,
                true
        );

        String jsonContent = json.write(response).getJson();

        assertThat(jsonContent).contains("\"servicesUnavailable\":false");
        assertThat(jsonContent).contains("\"employeesUnavailable\":true");
    }
}
