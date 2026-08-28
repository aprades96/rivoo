package com.rivoo.staff.infrastructure.adapter.out.rest;

import com.rivoo.staff.domain.exception.AuthServiceException;
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
 * The adapter must classify what happened to the auth-service call instead of flattening every
 * failure into a 502: a 5xx / connection failure / unusable response is a broken upstream (502),
 * while a 4xx is auth-service working correctly and refusing the request for a business reason
 * and must NOT be reported as a broken upstream.
 * <p>
 * The double is {@link MockRestServiceServer}, at the HTTP edge — strictly below the layer under
 * test, so each scenario is produced by a real response/transport condition travelling through
 * the real {@code RestClient} rather than by stubbing the adapter's own outcome.
 */
class AuthServiceAdapterTest {

    private static final String AUTH_SERVICE_URL = "http://auth-internal.rivoo.local:8081";
    private static final String REGISTER_EMPLOYEE_URI = AUTH_SERVICE_URL + "/api/internal/auth/register-employee";
    private static final String TENANT_ID = "sal_demo";
    private static final String KEYCLOAK_USER_ID = "3b7e5a10-1111-4222-8333-444455556666";
    private static final String DELETE_USER_URI = AUTH_SERVICE_URL + "/api/internal/auth/users/" + KEYCLOAK_USER_ID;

    private MockRestServiceServer server;
    private AuthServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new AuthServiceAdapter(builder, AUTH_SERVICE_URL);
    }

    private String registerEmployee() {
        return adapter.registerEmployee(TENANT_ID, "employee@example.com", "supersecret",
                "Ana", "Lopez", null);
    }

    @Test
    void registerEmployee_returnsKeycloakUserId_whenAuthServiceAccepts() {
        server.expect(requestTo(REGISTER_EMPLOYEE_URI))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"keycloakUserId":"%s"}
                        """.formatted(KEYCLOAK_USER_ID), MediaType.APPLICATION_JSON));

        assertThat(registerEmployee()).isEqualTo(KEYCLOAK_USER_ID);
        server.verify();
    }

    @Test
    void registerEmployee_authServiceAnswers409_isNotFlattenedIntoBadGateway() {
        // auth-service answered: this email already exists in Keycloak. Nothing is down.
        server.expect(requestTo(REGISTER_EMPLOYEE_URI))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        AuthServiceException exception = catchThrowableOfType(this::registerEmployee, AuthServiceException.class);

        assertThat(exception.getHttpStatus())
                .as("a dependency's business rejection must not be reported as a broken upstream")
                .isNotEqualTo(HttpStatus.BAD_GATEWAY)
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(exception.getErrorType()).isEqualTo("employee-registration-rejected");
        server.verify();
    }

    @Test
    void registerEmployee_authServiceAnswers400_isNotFlattenedIntoBadGateway() {
        // Keycloak's password policy refusing the password chosen for the employee.
        server.expect(requestTo(REGISTER_EMPLOYEE_URI))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        AuthServiceException exception = catchThrowableOfType(this::registerEmployee, AuthServiceException.class);

        assertThat(exception.getHttpStatus())
                .isNotEqualTo(HttpStatus.BAD_GATEWAY)
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        server.verify();
    }

    @Test
    void registerEmployee_authServiceAnswers500_stillSurfacesAsBadGateway() {
        server.expect(requestTo(REGISTER_EMPLOYEE_URI))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        AuthServiceException exception = catchThrowableOfType(this::registerEmployee, AuthServiceException.class);

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exception.getErrorType()).isEqualTo("auth-service-error");
        server.verify();
    }

    @Test
    void registerEmployee_authServiceRefusesTheConnection_stillSurfacesAsBadGateway() {
        server.expect(requestTo(REGISTER_EMPLOYEE_URI))
                .andExpect(method(POST))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        AuthServiceException exception = catchThrowableOfType(this::registerEmployee, AuthServiceException.class);

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exception.getErrorType()).isEqualTo("auth-service-error");
        server.verify();
    }

    @Test
    void registerEmployee_unreadableResponseBody_isCaughtByTheRestClientExceptionSafetyNet() {
        // A 2xx whose body RestClient cannot bind — here an edge/proxy HTML error page served with
        // a 200. RestClient raises UnknownContentTypeException, a RestClientException that is
        // NEITHER HttpClientErrorException, HttpServerErrorException nor ResourceAccessException,
        // so only the final safety net catches it. Deleting that catch is the exact change that
        // once shipped a production 500 from this package: the specific catches CLASSIFY, they
        // never replace the safety net.
        server.expect(requestTo(REGISTER_EMPLOYEE_URI))
                .andExpect(method(POST))
                .andRespond(withSuccess("<html><body>502 Bad Gateway</body></html>", MediaType.TEXT_HTML));

        AuthServiceException exception = catchThrowableOfType(this::registerEmployee, AuthServiceException.class);

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exception.getErrorType()).isEqualTo("auth-service-error");
        server.verify();
    }

    @Test
    void registerEmployee_2xxWithAnEmptyBody_surfacesAsBadGatewayInsteadOfNpe() {
        // What the old blanket catch (Exception) was absorbing: RestClient binds an empty 2xx to
        // null, and the log line dereferencing response.keycloakUserId() NPE'd inside the try.
        // With the narrowed catches that NPE would now escape as a 500, so the guard is explicit.
        server.expect(requestTo(REGISTER_EMPLOYEE_URI))
                .andExpect(method(POST))
                .andRespond(withSuccess());

        AuthServiceException exception = catchThrowableOfType(this::registerEmployee, AuthServiceException.class);

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exception.getErrorType()).isEqualTo("auth-service-error");
        server.verify();
    }

    @Test
    void registerEmployee_2xxWithoutAUsableKeycloakUserId_isNotReportedAsSuccess() {
        // The worse half of the same gap: a `{}` body did not even throw. The adapter returned
        // null and EmployeeService.create stored a null keycloakUserId, reporting success for an
        // employee that has no account to log in with.
        server.expect(requestTo(REGISTER_EMPLOYEE_URI))
                .andExpect(method(POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        AuthServiceException exception = catchThrowableOfType(this::registerEmployee, AuthServiceException.class);

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        server.verify();
    }

    @Test
    void deleteUser_authServiceAnswers404_isNotFlattenedIntoBadGateway() {
        // auth-service saying "no such user" is not an outage and must not raise an
        // infrastructure alarm.
        server.expect(requestTo(DELETE_USER_URI))
                .andExpect(method(DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        AuthServiceException exception = catchThrowableOfType(
                () -> adapter.deleteUser(KEYCLOAK_USER_ID), AuthServiceException.class);

        assertThat(exception.getHttpStatus())
                .isNotEqualTo(HttpStatus.BAD_GATEWAY)
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(exception.getErrorType()).isEqualTo("employee-registration-rejected");
        server.verify();
    }

    @Test
    void deleteUser_authServiceAnswers500_stillSurfacesAsBadGateway() {
        server.expect(requestTo(DELETE_USER_URI))
                .andExpect(method(DELETE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        AuthServiceException exception = catchThrowableOfType(
                () -> adapter.deleteUser(KEYCLOAK_USER_ID), AuthServiceException.class);

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        server.verify();
    }

    @Test
    void deleteUser_authServiceRefusesTheConnection_stillSurfacesAsBadGateway() {
        server.expect(requestTo(DELETE_USER_URI))
                .andExpect(method(DELETE))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        AuthServiceException exception = catchThrowableOfType(
                () -> adapter.deleteUser(KEYCLOAK_USER_ID), AuthServiceException.class);

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        server.verify();
    }
}
