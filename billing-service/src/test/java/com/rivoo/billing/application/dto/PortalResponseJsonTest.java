package com.rivoo.billing.application.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sibling of {@link SubscriptionResponseJsonTest} — see that class for why these
 * assertions are made against the serialized string rather than the record
 * accessor, and for the verification that no Jackson naming customization is in
 * play in this module.
 * <p>
 * {@code rivoo-frontend/src/lib/api/billing.ts} types the portal call as
 * {@code apiFetch<{ url: string }>} and the page does
 * {@code window.location.href = data.url}. If this component is ever renamed, the
 * assignment becomes {@code window.location.href = undefined} — no exception, no
 * failed request, the user simply does not go anywhere.
 */
@JsonTest
class PortalResponseJsonTest {

    @Autowired
    private JacksonTester<PortalResponse> json;

    @Test
    void serializesUrlUnderTheKeyTheFrontendReads() throws Exception {
        PortalResponse response = new PortalResponse("https://billing.stripe.com/mock-portal/abc");

        String jsonContent = json.write(response).getJson();

        assertThat(jsonContent).contains("\"url\"");
        assertThat(jsonContent).doesNotContain("\"portalUrl\"");
    }
}
