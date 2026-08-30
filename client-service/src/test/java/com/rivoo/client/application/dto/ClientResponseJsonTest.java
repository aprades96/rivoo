package com.rivoo.client.application.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for D37 (§2 D37 of the equipo-y-clientes plan): {@code ClientResponse} used to
 * omit {@code gdprConsentAt}, so the GDPR consent card in the client detail artboards rendered
 * "Consentimiento dado:" with no date. This locks the JSON property name so the mapper wiring
 * (MapStruct silently drops fields whose target name has no matching source, without any warning)
 * cannot regress unnoticed — same defect class as {@code EmployeeResponseJsonTest}.
 */
@JsonTest
class ClientResponseJsonTest {

    @Autowired
    private JacksonTester<ClientResponse> json;

    @Test
    void serializesGdprConsentAtField() throws Exception {
        Instant consentAt = Instant.parse("2026-01-15T09:30:00Z");
        ClientResponse response = new ClientResponse(
                "cli_abc123", "Ana", "Lopez", "ana@gmail.com", "+34600111222",
                "FEMALE", "WALK_IN", null, 3, Instant.now(), consentAt, true,
                Instant.now(), Instant.now());

        String jsonContent = json.write(response).getJson();

        assertThat(jsonContent).contains("\"gdprConsentAt\":\"2026-01-15T09:30:00Z\"");
    }
}
