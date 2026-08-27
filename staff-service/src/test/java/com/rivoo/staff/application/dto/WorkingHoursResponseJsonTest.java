package com.rivoo.staff.application.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the isOpen/open Jackson bug, run against the actual
 * serializer used in production. Sibling of
 * {@code salon-service}'s {@code BusinessHoursResponseJsonTest} (fixed in
 * 7d6ca6f) for the equivalent staff-service DTO.
 * <p>
 * Spring Boot 4 serializes HTTP responses with Jackson 3
 * ({@code tools.jackson.databind}), autoconfigured by
 * {@code spring-boot-starter-jackson}. {@code @JsonTest} boots the actual
 * Boot-autoconfigured {@link JacksonTester} (backed by
 * {@code tools.jackson.databind.json.JsonMapper}), so this test fails if the
 * {@code isOpen}/{@code open} naming is reintroduced at the level this slice
 * actually exercises.
 * <p>
 * {@link WorkingHoursResponse} is a bare record with no Jackson annotations
 * — verified with {@code grep -rn "JsonProperty" staff-service/src/main},
 * which returns nothing — so its component name ({@code isOpen}) is what
 * Jackson 3 uses as the property name by default. There is also no
 * {@code PropertyNamingStrategy}, {@code JsonMapperBuilderCustomizer} or
 * naming-related {@code Module}/{@code @JacksonComponent} bean anywhere in
 * this module or in {@code rivoo-common} — verified with
 * {@code grep -rln "PropertyNamingStrategy\|JsonMapperBuilderCustomizer\|JacksonComponent\|JsonComponent" staff-service/src/main rivoo-common/src/main},
 * which also returns nothing — so no global renaming can be in play here.
 */
@JsonTest
class WorkingHoursResponseJsonTest {

    @Autowired
    private JacksonTester<WorkingHoursResponse> json;

    @Test
    void serializesIsOpenField_notOpen() throws Exception {
        WorkingHoursResponse response = new WorkingHoursResponse(1, true,
                LocalTime.of(9, 0), LocalTime.of(18, 0), LocalTime.of(13, 0), LocalTime.of(14, 0));

        String jsonContent = json.write(response).getJson();

        assertThat(jsonContent).contains("\"isOpen\"");
        assertThat(jsonContent).doesNotContain("\"open\":");
    }
}
