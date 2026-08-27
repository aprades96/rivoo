package com.rivoo.salon.application.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the isOpen/open Jackson bug, run against the actual
 * serializer used in production.
 * <p>
 * Spring Boot 4 serializes HTTP responses with Jackson 3
 * ({@code tools.jackson.databind}), autoconfigured by
 * {@code spring-boot-starter-jackson}. A plain {@code new
 * com.fasterxml.jackson.databind.ObjectMapper()} (Jackson 2) is NOT that
 * serializer: it only happens to reach the classpath transitively via
 * flyway-mysql, is never wired into the HTTP response pipeline, and — unlike
 * the autoconfigured Jackson 3 mapper — has no jsr310 module registered, so
 * it throws on real (non-null) {@code LocalTime} values instead of exercising
 * the bug this test exists to catch.
 * <p>
 * {@code @JsonTest} boots the actual Boot-autoconfigured {@link JacksonTester}
 * (backed by {@code tools.jackson.databind.json.JsonMapper}), so this test
 * fails if the {@code isOpen}/{@code open} naming is reintroduced at the
 * level this slice actually exercises. {@link BusinessHoursResponse} is a
 * bare record with no Jackson annotations ({@code grep -rn "JsonProperty"
 * salon-service/src/main} returns nothing) — its component name
 * ({@code isOpen}) is what Jackson 3 uses as the property name by default.
 * What could still rename it is a Jackson {@code Module} bean registered on
 * the context (e.g. a {@code SimpleModule} installing a mixin), and
 * {@code @JsonTest} DOES auto-configure {@code Module} beans: verified by
 * temporarily registering exactly such a {@code SimpleModule} bean (renaming
 * {@code isOpen} to {@code open} via a mixin annotation) and confirming this
 * test's assertions failed while it was active.
 * <p>
 * What this test does NOT cover: {@code @JsonTest} does not perform a full
 * component scan of arbitrary {@code @Configuration} classes elsewhere in
 * the codebase. Verified directly: a standalone {@code @Configuration}
 * class (in the same package the real Jackson config lives in) declaring a
 * {@code JsonMapperBuilderCustomizer} that applies {@code SNAKE_CASE} naming
 * (which the real HTTP pipeline WOULD pick up via component scan, and which
 * would serialize this field as {@code is_open}) is invisible to this
 * slice — the test kept passing while that class was on the classpath. A
 * regression introduced through a {@code @Configuration}-declared
 * {@code JsonMapperBuilderCustomizer} or {@code PropertyNamingStrategy}
 * would therefore NOT be caught here; only a full {@code @SpringBootTest}
 * hitting the real endpoint (or an equivalent MockMvc-based web slice —
 * currently unavailable in this project, see module notes) would close that
 * gap.
 * <p>
 * Note: {@code org.springframework.boot.jackson.JsonComponent} does not
 * exist on this module's classpath in Spring Boot 4.0.3 — the annotation was
 * renamed to {@code @JacksonComponent} (package
 * {@code org.springframework.boot.jackson}). A {@code @JacksonComponent}
 * would presumably be auto-configured the same way {@code Module} beans are,
 * but that was not exercised here and is intentionally not claimed above.
 */
@JsonTest
class BusinessHoursResponseJsonTest {

    @Autowired
    private JacksonTester<BusinessHoursResponse> json;

    @Test
    void serializesIsOpenField_notOpen() throws Exception {
        BusinessHoursResponse response = new BusinessHoursResponse(1, true,
                LocalTime.of(9, 0), LocalTime.of(18, 0), LocalTime.of(13, 0), LocalTime.of(14, 0));

        String jsonContent = json.write(response).getJson();

        assertThat(jsonContent).contains("\"isOpen\"");
        assertThat(jsonContent).doesNotContain("\"open\":");
    }
}
