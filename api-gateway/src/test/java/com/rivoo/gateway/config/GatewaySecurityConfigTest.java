package com.rivoo.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Locks down the anonymous public-availability rule added to
 * {@link GatewaySecurityConfig}: GET is public (no JWT needed) but the path is
 * only permitted for GET, so any other method on the same path still requires
 * authentication. Nothing else in the module has a test guarding this
 * security-critical route table, so a future edit could silently widen or
 * narrow it without any test failing.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class GatewaySecurityConfigTest {

    private static final String PUBLIC_AVAILABILITY_PATH = "/api/v1/appointments/public/availability";

    @LocalServerPort
    private int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void getPublicAvailability_doesNotRequireAuthentication() {
        int statusCode = client().get()
                .uri(PUBLIC_AVAILABILITY_PATH)
                .exchange()
                .expectBody().returnResult()
                .getStatus()
                .value();

        // No live appointment-service is wired for this test, so the request
        // will not necessarily succeed end to end, but the point being asserted
        // here is exclusively that security does not reject it as unauthenticated.
        assertThat(statusCode).isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void postToPublicAvailabilityPath_stillRequiresAuthentication() {
        client().post()
                .uri(PUBLIC_AVAILABILITY_PATH)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
