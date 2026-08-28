package com.rivoo.salon.infrastructure.adapter.in.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rivoo.common.web.GlobalExceptionHandler;
import com.rivoo.salon.domain.exception.BillingServiceException;
import com.rivoo.salon.domain.port.in.GetSalonUseCase;
import com.rivoo.salon.domain.port.in.ListSalonsUseCase;
import com.rivoo.salon.domain.port.in.ManageBusinessHoursUseCase;
import com.rivoo.salon.domain.port.in.ManageSalonStatusUseCase;
import com.rivoo.salon.domain.port.in.RegisterSalonUseCase;
import com.rivoo.salon.domain.port.in.UpdateSalonUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for the defect fixed by making {@link BillingServiceException}
 * extend {@code RivooException}: before that change it extended a bare
 * {@code RuntimeException} with no {@code @ExceptionHandler} anywhere in the
 * monorepo, so it always fell through to {@link GlobalExceptionHandler}'s generic
 * {@code @ExceptionHandler(Exception.class)} catch-all: a deterministic 500
 * "An unexpected error occurred" on every billing-service outage during salon
 * registration.
 * <p>
 * {@link BillingServiceException} has a dedicated handler in
 * {@link SalonExceptionHandler#handleBillingServiceError(BillingServiceException)}
 * for two reasons. It carries a cause (see {@code BillingServiceAdapter}, which wraps the
 * original failure), and {@link GlobalExceptionHandler#handleRivooException}'s generic
 * {@code atWarn} (no {@code setCause}) would silently drop that stack trace. And that generic
 * handler publishes {@code ex.getMessage()} as the response {@code detail} — on an ANONYMOUS
 * endpoint, where the adapter's message names the dependency and the tenant.
 * <p>
 * This class exercises the handler in isolation (the exception is stubbed at the use-case
 * boundary). The classification that DECIDES between 502 and 422, and the end-to-end body an
 * anonymous caller receives, are covered below the port instead — see
 * {@code BillingServiceAdapterTest} and {@code SalonRegistrationDependencyContractTest}.
 */
class BillingServiceExceptionHandlingTest {

    /** The fixed, topology-free string SalonExceptionHandler publishes for a broken dependency. */
    private static final String CLIENT_SAFE_DETAIL =
            "Salon registration is temporarily unavailable. Please try again in a few minutes.";

    private RegisterSalonUseCase registerSalonUseCase;
    private MockMvc mockMvc;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        registerSalonUseCase = mock(RegisterSalonUseCase.class);
        SalonController controller = new SalonController(
                registerSalonUseCase,
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
        salonExceptionHandlerLogger().addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        salonExceptionHandlerLogger().detachAppender(logAppender);
    }

    private static Logger salonExceptionHandlerLogger() {
        return (Logger) LoggerFactory.getLogger(SalonExceptionHandler.class);
    }

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

    @Test
    void register_billingServiceUnreachable_returns502NotAnInternalServerError() throws Exception {
        when(registerSalonUseCase.register(org.mockito.ArgumentMatchers.any()))
                .thenThrow(BillingServiceException.unavailable(
                        "Failed to create subscription in billing-service for tenant: sal_new", null));

        mockMvc.perform(post("/api/v1/salons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.detail").value(CLIENT_SAFE_DETAIL))
                .andExpect(jsonPath("$.title").value("Billing Service Error"))
                .andExpect(jsonPath("$.type").value("https://rivoo.com/errors/billing-service-error"));
    }

    @Test
    void register_billingServiceUnreachable_logsThroughLocalHandlerWithCauseAtErrorLevel() throws Exception {
        // BillingServiceAdapter always wraps the original failure as the cause (see
        // BillingServiceAdapter#createSubscription) — losing it here would leave a
        // billing-service outage diagnosable only from a one-line WARN with no stack trace.
        Throwable upstreamCause = new RuntimeException("Connection refused");
        when(registerSalonUseCase.register(org.mockito.ArgumentMatchers.any()))
                .thenThrow(BillingServiceException.unavailable(
                        "Failed to create subscription in billing-service for tenant: sal_new", upstreamCause));

        mockMvc.perform(post("/api/v1/salons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isBadGateway());

        assertThat(logAppender.list)
                .as("SalonExceptionHandler.handleBillingServiceError (atError + setCause) must be the one that ran")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage()).isEqualTo("Billing service error");
                    assertThat(event.getThrowableProxy())
                            .as("atError().setCause(ex) must attach the exception, giving a stack trace")
                            .isNotNull();
                    assertThat(event.getThrowableProxy().getCause())
                            .as("the original upstream failure BillingServiceAdapter wrapped must survive into the log")
                            .isNotNull();
                });
        assertThat(logAppender.list)
                .as("GlobalExceptionHandler.handleRivooException (atWarn, no cause) must NOT be the one that ran")
                .noneMatch(event -> "Rivoo exception".equals(event.getFormattedMessage()));
    }
}
