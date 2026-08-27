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
 * fails if a future customization of that mapper (e.g. a
 * {@code PropertyNamingStrategy} or a {@code JsonMapperBuilderCustomizer})
 * reintroduces the bug.
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
