package com.rivoo.staff.application.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the active/isActive Jackson bug found in the Block 3 audit
 * (same defect class as {@code WorkingHoursResponseJsonTest} and
 * {@code ServiceOfferingResponseJsonTest}): the frontend's {@code Employee} type
 * (rivoo-frontend/src/types/employee.ts) declares {@code isActive}, but this DTO used to emit
 * {@code active}, so any client-side filter on {@code isActive} would silently always be false.
 * <p>
 * {@link EmployeeResponse} is a bare record with no Jackson annotations — verified with
 * {@code grep -rn "JsonProperty" staff-service/src/main}, which returns nothing — and there is no
 * {@code PropertyNamingStrategy} anywhere in this module or in {@code rivoo-common} — verified with
 * {@code grep -rln "PropertyNamingStrategy" staff-service/src/main rivoo-common/src/main}, which
 * also returns nothing — so the record component name ({@code isActive}) is exactly what Jackson 3
 * uses as the JSON property name.
 */
@JsonTest
class EmployeeResponseJsonTest {

    @Autowired
    private JacksonTester<EmployeeResponse> json;

    @Test
    void serializesIsActiveField_notActive() throws Exception {
        EmployeeResponse response = new EmployeeResponse(
                "emp_123", "Maria", "Garcia", "maria@salon.com", "+34600111222",
                "Estilista", "#3B82F6", "STYLIST", true, Instant.now(), Instant.now());

        String jsonContent = json.write(response).getJson();

        assertThat(jsonContent).contains("\"isActive\"");
        assertThat(jsonContent).doesNotContain("\"active\":");
    }
}
