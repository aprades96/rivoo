package com.rivoo.salon.infrastructure.adapter.out.rest;

import com.rivoo.salon.domain.exception.AuthServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Same property as {@code BillingServiceAdapterTest}, for the other dependency of the salon
 * onboarding saga: a 5xx / connection failure / unusable response from auth-service is a broken
 * upstream (502); a 4xx is auth-service working correctly and refusing the request, and must not
 * be flattened into 502.
 * <p>
 * The double is {@link MockRestServiceServer}, at the HTTP edge — below the layer under test.
 */
class AuthServiceAdapterTest {

    private static final String AUTH_SERVICE_URL = "http://auth-internal.rivoo.local:8081";
    private static final String REGISTER_OWNER_URI = AUTH_SERVICE_URL + "/api/internal/auth/register-owner";
    private static final String TENANT_ID = "sal_new";
    private static final String KEYCLOAK_USER_ID = "9f1c2d3e-0000-4444-8888-aaaabbbbcccc";

    private MockRestServiceServer server;
    private AuthServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new AuthServiceAdapter(builder, AUTH_SERVICE_URL);
    }

    private String registerOwner() {
        return adapter.registerOwner(TENANT_ID, "owner@example.com", "supersecret",
                "Ana", "Lopez", "Demo Salon", "FREE_TRIAL");
    }

    @Test
    void registerOwner_returnsKeycloakUserId_whenAuthServiceAccepts() {
        server.expect(requestTo(REGISTER_OWNER_URI))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"keycloakUserId":"%s","email":"owner@example.com","role":"SALON_OWNER"}
                        """.formatted(KEYCLOAK_USER_ID), MediaType.APPLICATION_JSON));

        assertThat(registerOwner()).isEqualTo(KEYCLOAK_USER_ID);
        server.verify();
    }

    @Test
    void registerOwner_authServiceAnswers409_isNotFlattenedIntoBadGateway() {
        // auth-service answered: this email already exists in Keycloak. Nothing is down.
        server.expect(requestTo(REGISTER_OWNER_URI))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        AuthServiceException exception = catchThrowableOfType(this::registerOwner, AuthServiceException.class);

        assertThat(exception.getHttpStatus())
                .as("a dependency's business rejection must not be reported as a broken upstream")
                .isNotEqualTo(HttpStatus.BAD_GATEWAY)
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(exception.getErrorType()).isEqualTo("salon-registration-rejected");
        server.verify();
    }

    @Test
    void registerOwner_authServiceAnswers400_isNotFlattenedIntoBadGateway() {
        server.expect(requestTo(REGISTER_OWNER_URI))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        AuthServiceException exception = catchThrowableOfType(this::registerOwner, AuthServiceException.class);

        assertThat(exception.getHttpStatus())
                .isNotEqualTo(HttpStatus.BAD_GATEWAY)
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        server.verify();
    }

    @Test
    void registerOwner_authServiceAnswers500_stillSurfacesAsBadGateway() {
        server.expect(requestTo(REGISTER_OWNER_URI))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        AuthServiceException exception = catchThrowableOfType(this::registerOwner, AuthServiceException.class);

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exception.getErrorType()).isEqualTo("auth-service-error");
        server.verify();
    }

    @Test
    void registerOwner_authServiceRefusesTheConnection_stillSurfacesAsBadGateway() {
        server.expect(requestTo(REGISTER_OWNER_URI))
                .andExpect(method(POST))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        AuthServiceException exception = catchThrowableOfType(this::registerOwner, AuthServiceException.class);

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exception.getErrorType()).isEqualTo("auth-service-error");
        server.verify();
    }

    @Test
    void registerOwner_unreadableResponseBody_isCaughtByTheRestClientExceptionSafetyNet() {
        // A 2xx whose body RestClient cannot bind — here an edge/proxy HTML error page served with
        // a 200. RestClient raises UnknownContentTypeException, a RestClientException that is
        // NEITHER HttpClientErrorException, HttpServerErrorException nor ResourceAccessException,
        // so only the final safety net catches it. Deleting that catch is the exact change that
        // once shipped a production 500 from this package: the specific catches CLASSIFY, they
        // never replace the safety net.
        server.expect(requestTo(REGISTER_OWNER_URI))
                .andExpect(method(POST))
                .andRespond(withSuccess("<html><body>502 Bad Gateway</body></html>", MediaType.TEXT_HTML));

        AuthServiceException exception = catchThrowableOfType(this::registerOwner, AuthServiceException.class);

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        server.verify();
    }

    @Test
    void registerOwner_2xxWithoutAUsableKeycloakUserId_surfacesAsBadGatewayNotAsANullOwner() {
        // Guards the gap the narrower catches would otherwise open: with a blanket
        // catch (Exception) gone, a null body would NPE into a blanket 500 — or worse, be stored
        // as a null ownerUserId.
        server.expect(requestTo(REGISTER_OWNER_URI))
                .andExpect(method(POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        AuthServiceException exception = catchThrowableOfType(this::registerOwner, AuthServiceException.class);

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        server.verify();
    }

    @Test
    void deleteUser_authServiceAnswers404_isNotFlattenedIntoBadGateway() {
        // The compensation path classifies the same way: auth-service saying "no such user" is
        // not an outage, and must not raise an infrastructure alarm.
        server.expect(requestTo(AUTH_SERVICE_URL + "/api/internal/auth/users/" + KEYCLOAK_USER_ID))
                .andExpect(method(DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        AuthServiceException exception = catchThrowableOfType(
                () -> adapter.deleteUser(KEYCLOAK_USER_ID), AuthServiceException.class);

        assertThat(exception.getHttpStatus())
                .isNotEqualTo(HttpStatus.BAD_GATEWAY)
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        server.verify();
    }

    @Test
    void deleteUser_authServiceAnswers500_stillSurfacesAsBadGateway() {
        server.expect(requestTo(AUTH_SERVICE_URL + "/api/internal/auth/users/" + KEYCLOAK_USER_ID))
                .andExpect(method(DELETE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        AuthServiceException exception = catchThrowableOfType(
                () -> adapter.deleteUser(KEYCLOAK_USER_ID), AuthServiceException.class);

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        server.verify();
    }
}
