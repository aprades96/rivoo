package com.rivoo.salon.infrastructure.adapter.in.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rivoo.common.web.GlobalExceptionHandler;
import com.rivoo.salon.application.OnboardingSagaService;
import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.port.in.GetSalonUseCase;
import com.rivoo.salon.domain.port.in.ListSalonsUseCase;
import com.rivoo.salon.domain.port.in.ManageBusinessHoursUseCase;
import com.rivoo.salon.domain.port.in.ManageSalonStatusUseCase;
import com.rivoo.salon.domain.port.in.UpdateSalonUseCase;
import com.rivoo.salon.domain.port.out.BusinessHoursPersistencePort;
import com.rivoo.salon.domain.port.out.NotificationServicePort;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import com.rivoo.salon.infrastructure.adapter.out.rest.AuthServiceAdapter;
import com.rivoo.salon.infrastructure.adapter.out.rest.BillingServiceAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The end-to-end HTTP contract of the ANONYMOUS {@code POST /api/v1/salons}, with the real
 * controller, the real {@link OnboardingSagaService}, the real outbound adapters and both advices
 * wired exactly as at runtime. The only doubles are the two persistence ports and, crucially, the
 * HTTP edge itself ({@link MockRestServiceServer}) — strictly BELOW the layer that implements both
 * properties under test, so neither the adapters' classification nor the handler's response body
 * can be stubbed into passing.
 * <p>
 * Two properties are pinned here:
 * <ol>
 *   <li><b>A dependency's 4xx is not an outage.</b> billing-service answering 422 must reach the
 *       caller as 422, not as the 502 this endpoint used to return for every failure.</li>
 *   <li><b>No internal topology in the response.</b> The exception's message names the dependency
 *       and the tenant, and its cause carries the full internal URL — host and port. On an
 *       unauthenticated endpoint none of that may appear in the body; it must appear in the LOG
 *       instead, which the last test asserts explicitly rather than merely asserting its absence
 *       from the response (deleting the diagnostic would otherwise pass just as well).</li>
 * </ol>
 */
class SalonRegistrationDependencyContractTest {

    // Deliberately host:port shaped: these literals are what a leak would put in the response.
    private static final String AUTH_SERVICE_URL = "http://auth-internal.rivoo.local:8081";
    private static final String BILLING_SERVICE_URL = "http://billing-internal.rivoo.local:8087";
    private static final String REGISTER_OWNER_URI = AUTH_SERVICE_URL + "/api/internal/auth/register-owner";
    private static final String SUBSCRIPTIONS_URI = BILLING_SERVICE_URL + "/api/internal/billing/subscriptions";
    private static final String KEYCLOAK_USER_ID = "9f1c2d3e-0000-4444-8888-aaaabbbbcccc";

    private static final String UNAVAILABLE_DETAIL =
            "Salon registration is temporarily unavailable. Please try again in a few minutes.";
    private static final String REJECTED_DETAIL =
            "Salon registration could not be completed. Please review the details provided and try again.";

    private static final String REQUEST_BODY = """
            {
              "name": "Demo Salon",
              "email": "owner@example.com",
              "phone": "+34600000000",
              "addressStreet": "Carrer Demo 1",
              "addressPostalCode": "08001",
              "ownerFirstName": "Ana",
              "ownerLastName": "Lopez",
              "ownerPassword": "supersecret"
            }
            """;

    private MockRestServiceServer authServer;
    private MockRestServiceServer billingServer;
    private MockMvc mockMvc;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        RestClient.Builder authBuilder = RestClient.builder();
        authServer = MockRestServiceServer.bindTo(authBuilder).build();
        RestClient.Builder billingBuilder = RestClient.builder();
        billingServer = MockRestServiceServer.bindTo(billingBuilder).build();

        SalonPersistencePort salonPersistencePort = mock(SalonPersistencePort.class);
        when(salonPersistencePort.existsByEmail(any())).thenReturn(false);
        when(salonPersistencePort.existsBySlug(any())).thenReturn(false);
        when(salonPersistencePort.save(any())).thenAnswer(invocation -> {
            Salon salon = invocation.getArgument(0);
            if (salon.getId() == null) {
                salon.setId(1L);
            }
            return salon;
        });

        OnboardingSagaService saga = new OnboardingSagaService(
                salonPersistencePort,
                mock(BusinessHoursPersistencePort.class),
                new AuthServiceAdapter(authBuilder, AUTH_SERVICE_URL),
                new BillingServiceAdapter(billingBuilder, BILLING_SERVICE_URL),
                mock(NotificationServicePort.class));

        SalonController controller = new SalonController(
                saga,
                mock(GetSalonUseCase.class),
                mock(UpdateSalonUseCase.class),
                mock(ManageBusinessHoursUseCase.class),
                mock(ManageSalonStatusUseCase.class),
                mock(ListSalonsUseCase.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new SalonExceptionHandler())
                .build();

        logAppender = new ListAppender<>();
        logAppender.start();
        handlerLogger().addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        handlerLogger().detachAppender(logAppender);
    }

    private static Logger handlerLogger() {
        return (Logger) LoggerFactory.getLogger(SalonExceptionHandler.class);
    }

    private void expectOwnerRegistrationSucceeds() {
        authServer.expect(requestTo(REGISTER_OWNER_URI))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"keycloakUserId":"%s","email":"owner@example.com","role":"SALON_OWNER"}
                        """.formatted(KEYCLOAK_USER_ID), MediaType.APPLICATION_JSON));
    }

    /** The saga compensates a failed subscription by deleting the Keycloak user it just created. */
    private void expectCompensatingUserDeletion() {
        authServer.expect(requestTo(AUTH_SERVICE_URL + "/api/internal/auth/users/" + KEYCLOAK_USER_ID))
                .andExpect(method(DELETE))
                .andRespond(withSuccess());
    }

    private String register() throws Exception {
        return mockMvc.perform(post("/api/v1/salons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void register_billingServiceRejectsWith422_answers422NotBadGateway() throws Exception {
        expectOwnerRegistrationSucceeds();
        billingServer.expect(requestTo(SUBSCRIPTIONS_URI))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));
        expectCompensatingUserDeletion();

        mockMvc.perform(post("/api/v1/salons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(REJECTED_DETAIL))
                .andExpect(jsonPath("$.title").value("Salon Registration Rejected"))
                .andExpect(jsonPath("$.type").value("https://rivoo.com/errors/salon-registration-rejected"));

        authServer.verify();
        billingServer.verify();
    }

    @Test
    void register_billingServiceAnswers500_stillAnswers502() throws Exception {
        // The other half of the classification: a genuinely broken dependency keeps its 502.
        expectOwnerRegistrationSucceeds();
        billingServer.expect(requestTo(SUBSCRIPTIONS_URI))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        expectCompensatingUserDeletion();

        mockMvc.perform(post("/api/v1/salons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                .andExpect(jsonPath("$.type").value("https://rivoo.com/errors/billing-service-error"));

        authServer.verify();
        billingServer.verify();
    }

    @Test
    void register_authServiceRejectsWith409_answers422NotBadGateway() throws Exception {
        authServer.expect(requestTo(REGISTER_OWNER_URI))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/v1/salons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(REJECTED_DETAIL))
                .andExpect(jsonPath("$.type").value("https://rivoo.com/errors/salon-registration-rejected"));

        authServer.verify();
    }

    @Test
    void register_authServiceUnreachable_answers502AndLeaksNoInternalTopology() throws Exception {
        // The connection failure is what makes the ResourceAccessException message carry the full
        // internal URL — host and port. That message is the one that used to reach the response.
        authServer.expect(requestTo(REGISTER_OWNER_URI))
                .andExpect(method(POST))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        String body = register();
        authServer.verify();

        assertThat(body)
                .as("an unauthenticated caller must learn nothing about the internal topology")
                .doesNotContain("auth-internal.rivoo.local")
                .doesNotContain("8081")
                .doesNotContain("/api/internal/")
                .doesNotContain("Connection refused")
                .doesNotContain("I/O error")
                .as("the tenant id the saga minted internally must not be echoed either")
                .doesNotContain("sal_");
        assertThat(body).contains("\"detail\":\"" + UNAVAILABLE_DETAIL + "\"");
        assertThat(body).contains("\"status\":502");

        // Deliberately NOT asserted: that the bare token "auth-service" is absent from the whole
        // body. It survives in the published `type` (.../errors/auth-service-error) and `title`
        // ("Auth Service Error"), which predate this change, are part of the error taxonomy other
        // code and dashboards key on, and carry no host, port, path or identifier. Renaming them
        // is a contract change in its own right and is out of scope here - see the report.
    }

    @Test
    void register_authServiceUnreachable_movesTheDiagnosticToTheLogInsteadOfDroppingIt() throws Exception {
        // Deleting the leaked detail is only half the fix: an operator still has to be able to see
        // which dependency failed and at which URL. This asserts the information MOVED — same run,
        // same failure, absent from the body (test above) and present in the log (here).
        authServer.expect(requestTo(REGISTER_OWNER_URI))
                .andExpect(method(POST))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        register();
        authServer.verify();

        assertThat(logAppender.list)
                .as("SalonExceptionHandler must log the real cause with the fluent API")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage()).isEqualTo("Auth service error");
                    assertThat(event.getKeyValuePairs())
                            .as("the message removed from the response must be present as a log field")
                            .anySatisfy(pair -> {
                                assertThat(pair.key).isEqualTo("internalDetail");
                                assertThat(String.valueOf(pair.value)).contains("auth-service");
                            });
                    assertThat(event.getKeyValuePairs())
                            .anySatisfy(pair -> {
                                assertThat(pair.key).isEqualTo("dependency");
                                assertThat(pair.value).isEqualTo("auth-service");
                            });
                    assertThat(event.getThrowableProxy())
                            .as("setCause(ex) must attach the exception so the stack trace survives")
                            .isNotNull();
                    assertThat(event.getThrowableProxy().getCause())
                            .as("the ResourceAccessException naming the internal URL must survive into the log")
                            .isNotNull();
                    assertThat(event.getThrowableProxy().getCause().getMessage())
                            .as("the URL removed from the response body must still be diagnosable")
                            .contains("auth-internal.rivoo.local:8081");
                });
    }
}
