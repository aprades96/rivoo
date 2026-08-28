package com.rivoo.salon.application.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the wire name of {@link SalonResponse#onboardingCompletedAt()}, run against the actual
 * serializer used in production ({@code @JsonTest} boots the Boot-autoconfigured
 * {@link JacksonTester} - see {@code BusinessHoursResponseJsonTest} for how that was verified to be
 * the real HTTP-response serializer in this module).
 * <p>
 * This field is the only signal the frontend's onboarding assistant uses to decide whether the
 * owner may proceed into the application: a silent rename here, or a {@code null} that failed to
 * serialize as JSON {@code null}, would strand every owner who just finished onboarding in the
 * assistant with no visible error. {@link SalonResponse} is a bare record with no Jackson
 * annotations ({@code grep -rn "JsonProperty" salon-service/src/main} returns nothing), so its wire
 * name is exactly its record component name, {@code onboardingCompletedAt} - this test exists as a
 * regression lock on that name and on the {@code null} case, following the same pattern as
 * {@link SalonPublicResponseJsonTest} and {@link BusinessHoursResponseJsonTest}.
 */
@JsonTest
class SalonResponseJsonTest {

    @Autowired
    private JacksonTester<SalonResponse> json;

    @Test
    void serializesOnboardingCompletedAtUnderItsOwnName() throws Exception {
        Instant completedAt = Instant.parse("2026-03-25T09:00:00Z");
        SalonResponse response = withOnboardingCompletedAt(completedAt);

        String jsonContent = json.write(response).getJson();

        assertThat(jsonContent).contains("\"onboardingCompletedAt\":\"2026-03-25T09:00:00Z\"");
    }

    @Test
    void serializesNullOnboardingCompletedAtAsJsonNull_notAsAnAbsentOrTruthyField() throws Exception {
        SalonResponse response = withOnboardingCompletedAt(null);

        String jsonContent = json.write(response).getJson();

        assertThat(jsonContent).contains("\"onboardingCompletedAt\":null");
    }

    private static SalonResponse withOnboardingCompletedAt(Instant onboardingCompletedAt) {
        return new SalonResponse(
                "sal_demo",
                "Demo Salon",
                "salon-demo",
                "demo@example.com",
                "+34600000000",
                "A demo salon",
                null,
                null,
                "Carrer Demo 1",
                "Barcelona",
                "08001",
                "Europe/Madrid",
                "EUR",
                "FREE_TRIAL",
                "ACTIVE",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                onboardingCompletedAt
        );
    }
}
