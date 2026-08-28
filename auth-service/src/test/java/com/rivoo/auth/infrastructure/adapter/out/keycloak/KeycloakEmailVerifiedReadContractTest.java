package com.rivoo.auth.infrastructure.adapter.out.keycloak;

import com.rivoo.auth.domain.port.out.KeycloakAdminPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Keycloak is the only place the answer to "has this person proved the address is theirs?" exists,
 * and a salon becomes publicly visible on the strength of it. So this reads the real
 * {@code emailVerified} field off the real user representation, at the HTTP boundary.
 * <p>
 * The two response shapes below are deliberately different data, not the same stub twice: an
 * explicit {@code false}, and a representation where Keycloak simply omitted the field. Both must
 * come back as "not verified", because only an explicit {@code true} may publish a salon.
 */
class KeycloakEmailVerifiedReadContractTest {

    private static final String BASE_URL = "http://keycloak.test/admin/realms/rivoo";
    private static final String USER_ID = "11111111-2222-3333-4444-555555555555";
    private static final String USER_URI = BASE_URL + "/users/" + USER_ID;

    private MockRestServiceServer keycloak;
    private KeycloakAdminPort adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        keycloak = MockRestServiceServer.bindTo(builder).build();
        KeycloakTokenManager tokenManager = mock(KeycloakTokenManager.class);
        when(tokenManager.getAccessToken()).thenReturn("stub-admin-token");
        adapter = new KeycloakAdminAdapter(builder.build(), tokenManager, BASE_URL);
    }

    private void respondWith(String userJson) {
        keycloak.expect(requestTo(USER_URI))
                .andExpect(method(GET))
                .andRespond(withSuccess(userJson, MediaType.APPLICATION_JSON));
    }

    @Test
    void confirmedAddress_readsTrue() {
        respondWith("""
                {"id":"%s","username":"owner@example.com","email":"owner@example.com",
                 "enabled":true,"emailVerified":true}
                """.formatted(USER_ID));

        assertThat(adapter.isEmailVerified(USER_ID)).isTrue();

        keycloak.verify();
    }

    @Test
    void unconfirmedAddress_readsFalse() {
        respondWith("""
                {"id":"%s","username":"owner@example.com","email":"owner@example.com",
                 "enabled":true,"emailVerified":false,"requiredActions":["VERIFY_EMAIL"]}
                """.formatted(USER_ID));

        assertThat(adapter.isEmailVerified(USER_ID)).isFalse();

        keycloak.verify();
    }

    @Test
    void representationWithoutTheField_readsFalseRatherThanBlowingUp() {
        // A missing flag is not proof of verification, and it must not NPE on the unboxing either.
        respondWith("""
                {"id":"%s","username":"owner@example.com","email":"owner@example.com","enabled":true}
                """.formatted(USER_ID));

        assertThat(adapter.isEmailVerified(USER_ID)).isFalse();

        keycloak.verify();
    }
}
