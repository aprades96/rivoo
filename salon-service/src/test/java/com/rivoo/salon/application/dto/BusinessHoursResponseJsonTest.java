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
 * fails if the {@code isOpen}/{@code open} bug is reintroduced at the level
 * this slice actually exercises: the record itself (e.g. reverting the
 * {@code @JsonProperty("isOpen")} annotation) or a {@code @JsonComponent} /
 * Jackson module registered on the classpath, both of which {@code @JsonTest}
 * auto-configures.
 * <p>
 * What this test does NOT cover: {@code @JsonTest} only auto-configures
 * {@code @JsonComponent} beans, serializers and modules — it does NOT load
 * arbitrary {@code @Configuration} classes. This was verified directly: a
 * {@code @Configuration} declaring a {@code JsonMapperBuilderCustomizer}
 * that applies {@code SNAKE_CASE} naming (which the real HTTP pipeline would
 * pick up via component scan, and which would serialize this field as
 * {@code is_open}) is invisible to this slice — the test kept passing while
 * that customizer was active. A regression introduced through a
 * {@code @Configuration}-based {@code JsonMapperBuilderCustomizer} or
 * {@code PropertyNamingStrategy} would therefore NOT be caught here; only a
 * full {@code @SpringBootTest} hitting the real endpoint (or an equivalent
 * MockMvc-based web slice — currently unavailable in this project, see
 * module notes) would close that gap.
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
