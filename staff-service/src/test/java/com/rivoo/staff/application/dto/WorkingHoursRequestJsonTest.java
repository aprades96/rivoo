package com.rivoo.staff.application.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the write-side half of the isOpen/open bug: before this
 * fix, the frontend sent {@code "isOpen"} in the request body, the record
 * component was named {@code open}, Jackson 3 silently ignored the unknown
 * {@code isOpen} key (no {@code @JsonIgnoreProperties(ignoreUnknown = false)}
 * is configured anywhere — verified with the same grep as
 * {@code WorkingHoursResponseJsonTest}), and {@code open} defaulted to
 * {@code false}. Every day was saved as closed regardless of what the owner
 * picked in the editor. This is the half of the bug that does the most
 * damage (data loss on save), per the task description.
 * <p>
 * Uses the actual Boot-autoconfigured {@link JacksonTester}
 * ({@code tools.jackson.databind}, Jackson 3), the same deserializer that
 * backs {@code @RequestBody WorkingHoursRequest} on the real endpoint — not
 * a hand-built {@code com.fasterxml.jackson.databind.ObjectMapper}, which is
 * a different library never wired into the HTTP request pipeline.
 */
@JsonTest
class WorkingHoursRequestJsonTest {

    @Autowired
    private JacksonTester<WorkingHoursRequest> json;

    @Test
    void deserializesIsOpenField_asTrue() throws Exception {
        String content = """
                {
                  "dayOfWeek": 1,
                  "isOpen": true,
                  "openTime": "09:00:00",
                  "closeTime": "18:00:00",
                  "breakStartTime": "13:00:00",
                  "breakEndTime": "14:00:00"
                }
                """;

        WorkingHoursRequest request = json.parseObject(content);

        assertThat(request.isOpen())
                .as("the isOpen the frontend sends must actually reach the isOpen record component")
                .isTrue();
        assertThat(request.openTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(request.closeTime()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void deserializesIsOpenField_asFalse() throws Exception {
        String content = """
                {
                  "dayOfWeek": 7,
                  "isOpen": false
                }
                """;

        WorkingHoursRequest request = json.parseObject(content);

        assertThat(request.isOpen()).isFalse();
    }

    @Test
    void aStrayOldOpenKey_isIgnored_onlyIsOpenDrivesTheFlag() throws Exception {
        // Documents the exact prior failure mode: a body carrying the old "open" key
        // (what the backend used to read) alongside "isOpen" must NOT let "open" win.
        // Before this fix "open" was the only key read; now it is an unrecognized
        // property (fail-on-unknown-properties is disabled, so it is silently
        // dropped instead of raising an error) and "isOpen" is the only one honored.
        String content = """
                {
                  "dayOfWeek": 1,
                  "isOpen": false,
                  "open": true
                }
                """;

        WorkingHoursRequest request = json.parseObject(content);

        assertThat(request.isOpen())
                .as("the stray \"open\" key from the old contract must not override \"isOpen\"")
                .isFalse();
    }
}
