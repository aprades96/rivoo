package com.rivoo.appointment.infrastructure.adapter.out.rest;

import com.rivoo.appointment.domain.exception.SalonNotFoundException;
import com.rivoo.appointment.domain.exception.SalonServiceUnavailableException;
import com.rivoo.appointment.domain.port.out.SalonServicePort;
import com.rivoo.common.web.GlobalExceptionHandler;
import com.rivoo.common.web.RivooErrorTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifies that {@link SalonServiceAdapter#getSalonBySlug(String)} correctly classifies every
 * failure mode of the call to salon-service:
 * <ul>
 *   <li>a 404 carrying the marker salon-service's own {@code SalonExceptionHandler} sets on a
 *       genuine "no salon for this slug" response → {@link SalonNotFoundException} (a domain
 *       "not found", not an opaque server error), so the two anonymous public flows can turn it
 *       into the same response they give for a salon that exists but is not ACTIVE — see
 *       {@code AvailabilityService} and {@code AppointmentService#book};</li>
 *   <li>a 404 WITHOUT that marker (e.g. a misconfigured {@code rivoo.services.salon-service.url},
 *       a renamed route, or an unrelated gateway 404) → a plain {@code RuntimeException} (a 500),
 *       never {@link SalonNotFoundException} — treating every 404 as "unknown salon" would turn a
 *       broken booking funnel into a silent, alert-free outage;</li>
 *   <li>a genuine upstream failure — salon-service responding with a 5xx, or being unreachable
 *       altogether — → {@link SalonServiceUnavailableException} (502/503), which tells the caller
 *       the failure is salon-service's, not ours, and that a retry may help.</li>
 * </ul>
 */
class SalonServiceAdapterTest {

    private static final String SALON_SERVICE_URL = "http://salon";

    private MockRestServiceServer server;
    private SalonServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new SalonServiceAdapter(builder, SALON_SERVICE_URL);
    }

    @Test
    void getSalonBySlug_returnsSalonInfo_whenSalonServiceRespondsWithSuccess() {
        server.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/bella-vista"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"id":"sal_A","name":"Bella Vista","slug":"bella-vista","status":"ACTIVE"}
                        """, MediaType.APPLICATION_JSON));

        SalonServicePort.SalonInfo result = adapter.getSalonBySlug("bella-vista");

        assertThat(result.tenantId()).isEqualTo("sal_A");
        assertThat(result.name()).isEqualTo("Bella Vista");
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void getSalonBySlug_throwsSalonNotFoundException_when404CarriesTheGenuineNotFoundMarker() {
        server.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/ghost-slug"))
                .andExpect(method(GET))
                .andRespond(withStatus(NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON).body("""
                        {"type":"%s","title":"Salon Not Found",
                         "status":404,"detail":"Salon not found: ghost-slug"}
                        """.formatted(RivooErrorTypes.SALON_NOT_FOUND)));

        // Must be the domain "not found" exception, not the generic RuntimeException that
        // used to blanket-wrap every failure (which surfaced as a 500 via the catch-all
        // handler and let anyone distinguish "unknown slug" from "suspended salon").
        assertThatThrownBy(() -> adapter.getSalonBySlug("ghost-slug"))
                .isExactlyInstanceOf(SalonNotFoundException.class);
    }

    @Test
    void getSalonBySlug_throwsRuntimeException_when404IsMissingTheGenuineNotFoundMarker() {
        // A 404 that does NOT come from salon-service's own SalonExceptionHandler — e.g. a
        // misconfigured URL, a renamed path, or a gateway/proxy 404 — must NOT be read as
        // "the slug doesn't exist": that would silently turn an operability incident into
        // 404s for every anonymous request, with no 5xx to alert anyone.
        server.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/bella-vista"))
                .andExpect(method(GET))
                .andRespond(withStatus(NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("""
                        {"error":"Not Found"}
                        """));

        assertThatThrownBy(() -> adapter.getSalonBySlug("bella-vista"))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(SalonNotFoundException.class)
                .isNotInstanceOf(SalonServiceUnavailableException.class);
    }

    @Test
    void getSalonBySlug_throwsRuntimeException_when404HasNoBodyAtAll() {
        // Same operability concern as above, for the simplest possible case: no body to even
        // look for a marker in.
        server.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/bella-vista"))
                .andExpect(method(GET))
                .andRespond(withStatus(NOT_FOUND));

        assertThatThrownBy(() -> adapter.getSalonBySlug("bella-vista"))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(SalonNotFoundException.class)
                .isNotInstanceOf(SalonServiceUnavailableException.class);
    }

    @Test
    void getSalonBySlug_throwsSalonServiceUnavailableWith502_whenSalonServiceRespondsWithServerError() {
        server.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/bella-vista"))
                .andExpect(method(GET))
                .andRespond(withStatus(INTERNAL_SERVER_ERROR));

        // An actual upstream failure (not a "the slug doesn't exist" case) must be reported as
        // salon-service's problem (502), not silently folded into "unknown salon", and not as
        // a plain 500 that hides that the failure is a downstream dependency.
        SalonServiceUnavailableException exception = catchThrowableOfType(
                () -> adapter.getSalonBySlug("bella-vista"), SalonServiceUnavailableException.class);

        assertThat(exception).isNotInstanceOf(SalonNotFoundException.class);
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertBodyPinnedToSlug(exception, "salon-service returned a server error for slug: bella-vista");
    }

    @Test
    void getSalonBySlug_throwsSalonServiceUnavailableWith503_whenSalonServiceIsUnreachable() {
        server.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/bella-vista"))
                .andExpect(method(GET))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        SalonServiceUnavailableException exception = catchThrowableOfType(
                () -> adapter.getSalonBySlug("bella-vista"), SalonServiceUnavailableException.class);

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertBodyPinnedToSlug(exception, "salon-service is unreachable for slug: bella-vista");
    }

    @Test
    void serverError_sameBodyShapeForAnySlug_onlyDetailEchoesTheRequestedSlug() {
        // Pins the actual invariant SalonServiceUnavailableException's javadoc documents: status,
        // type and title never vary with the slug. detail DOES include the slug — but it must be
        // EXACTLY "... for slug: <slug>", nothing more: an exact match here (not merely checking
        // getHttpStatus(), as this test class used to) is what would catch a future regression
        // that enriches the message with the salon's internal state.
        // Both expectations must be registered before either request fires: MockRestServiceServer
        // rejects new expectations once a request has already been made against it.
        server.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/salon-a"))
                .andExpect(method(GET))
                .andRespond(withStatus(INTERNAL_SERVER_ERROR));
        server.expect(requestTo(SALON_SERVICE_URL + "/api/internal/salons/by-slug/salon-b"))
                .andExpect(method(GET))
                .andRespond(withStatus(INTERNAL_SERVER_ERROR));

        SalonServiceUnavailableException exceptionA = catchThrowableOfType(
                () -> adapter.getSalonBySlug("salon-a"), SalonServiceUnavailableException.class);
        SalonServiceUnavailableException exceptionB = catchThrowableOfType(
                () -> adapter.getSalonBySlug("salon-b"), SalonServiceUnavailableException.class);

        ProblemDetail problemA = new GlobalExceptionHandler().handleRivooException(exceptionA);
        ProblemDetail problemB = new GlobalExceptionHandler().handleRivooException(exceptionB);

        assertThat(problemA.getStatus()).isEqualTo(problemB.getStatus());
        assertThat(problemA.getType()).isEqualTo(problemB.getType());
        assertThat(problemA.getTitle()).isEqualTo(problemB.getTitle());
        assertThat(problemA.getDetail()).isEqualTo("salon-service returned a server error for slug: salon-a");
        assertThat(problemB.getDetail()).isEqualTo("salon-service returned a server error for slug: salon-b");
    }

    /**
     * Builds the exact {@link ProblemDetail} an anonymous caller would receive (via
     * {@link GlobalExceptionHandler#handleRivooException}) and pins every field, including the
     * full, exact {@code detail} — not just {@link SalonServiceUnavailableException#getHttpStatus()}.
     */
    private static void assertBodyPinnedToSlug(SalonServiceUnavailableException exception, String expectedDetail) {
        ProblemDetail problem = new GlobalExceptionHandler().handleRivooException(exception);

        assertThat(problem.getStatus()).isEqualTo(exception.getHttpStatus().value());
        assertThat(problem.getType()).isEqualTo(URI.create("https://rivoo.com/errors/" + exception.getErrorType()));
        assertThat(problem.getTitle()).isEqualTo("Salon Service Unavailable");
        assertThat(problem.getDetail()).isEqualTo(expectedDetail);
    }
}
