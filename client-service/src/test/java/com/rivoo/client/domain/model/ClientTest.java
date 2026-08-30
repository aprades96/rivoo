package com.rivoo.client.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ClientTest {

    // ── registerVisit ────────────────────────────────────────────────────

    @Test
    void registerVisit_laterVisit_incrementsCounterAndAdvancesLastVisitAt() {
        Client client = Client.builder()
                .totalVisits(2)
                .lastVisitAt(Instant.parse("2026-07-01T10:00:00Z"))
                .build();

        client.registerVisit(Instant.parse("2026-08-05T10:00:00Z"));

        assertThat(client.getTotalVisits()).isEqualTo(3);
        assertThat(client.getLastVisitAt()).isEqualTo(Instant.parse("2026-08-05T10:00:00Z"));
    }

    @Test
    void registerVisit_earlierVisit_incrementsCounterButDoesNotRewindLastVisitAt() {
        Client client = Client.builder()
                .totalVisits(2)
                .lastVisitAt(Instant.parse("2026-07-01T10:00:00Z"))
                .build();

        client.registerVisit(Instant.parse("2026-06-01T10:00:00Z"));

        assertThat(client.getTotalVisits()).isEqualTo(3);
        assertThat(client.getLastVisitAt()).isEqualTo(Instant.parse("2026-07-01T10:00:00Z"));
    }

    @Test
    void registerVisit_noPriorVisit_setsLastVisitAtToTheGivenInstant() {
        Client client = Client.builder()
                .totalVisits(0)
                .lastVisitAt(null)
                .build();

        client.registerVisit(Instant.parse("2026-08-05T10:00:00Z"));

        assertThat(client.getTotalVisits()).isEqualTo(1);
        assertThat(client.getLastVisitAt()).isEqualTo(Instant.parse("2026-08-05T10:00:00Z"));
    }

    // ── anonymize clears the visit counters (D36) ──────────────────────────

    @Test
    void anonymize_clearsTotalVisitsAndLastVisitAt() {
        Client client = Client.builder()
                .firstName("Maria")
                .lastName("Garcia")
                .totalVisits(14)
                .lastVisitAt(Instant.parse("2026-08-05T10:00:00Z"))
                .active(true)
                .build();

        client.anonymize();

        assertThat(client.getTotalVisits()).isZero();
        assertThat(client.getLastVisitAt()).isNull();
        assertThat(client.isAnonymized()).isTrue();
    }
}
