package com.rivoo.salon.infrastructure.adapter.out.rest;

import com.rivoo.salon.domain.exception.BillingServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Pins how {@link BillingServiceAdapter} classifies each failure mode of the call to
 * billing-service.
 * <p>
 * The double sits at the HTTP edge ({@link MockRestServiceServer}), i.e. strictly BELOW the layer
 * that implements the property under test. Mocking {@code BillingServicePort} instead would let a
 * test stub the very exception it claims the adapter produces, and would stay green with the
 * adapter's classification fully reverted.
 * <p>
 * The property: a 5xx or a connection failure means billing-service is broken (502). A 4xx means
 * billing-service worked and refused the request for a business reason — it must NOT be flattened
 * into 502, which would tell the caller "the upstream is broken" and page an operator about a
 * healthy dependency.
 */
class BillingServiceAdapterTest {

    private static final String BILLING_SERVICE_URL = "http://billing-internal.rivoo.local:8087";
    private static final String SUBSCRIPTIONS_URI = BILLING_SERVICE_URL + "/api/internal/billing/subscriptions";
    private static final String TENANT_ID = "sal_new";

    private MockRestServiceServer server;
    private BillingServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new BillingServiceAdapter(builder, BILLING_SERVICE_URL);
    }

    @Test
    void createSubscription_succeeds_whenBillingServiceAccepts() {
        server.expect(requestTo(SUBSCRIPTIONS_URI)).andExpect(method(POST)).andRespond(withSuccess());

        assertThatCode(() -> adapter.createSubscription(TENANT_ID, "owner@example.com", "Demo Salon"))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void createSubscription_billingServiceAnswers422_isNotFlattenedIntoBadGateway() {
        // The defect this test exists for: billing-service answered — correctly — that it will not
        // create this subscription. That is a business rejection, not an outage.
        server.expect(requestTo(SUBSCRIPTIONS_URI))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        BillingServiceException exception = catchThrowableOfType(
                () -> adapter.createSubscription(TENANT_ID, "owner@example.com", "Demo Salon"),
                BillingServiceException.class);

        assertThat(exception.getHttpStatus())
                .as("a dependency's business rejection must not be reported as a broken upstream")
                .isNotEqualTo(HttpStatus.BAD_GATEWAY)
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(exception.getErrorType()).isEqualTo("salon-registration-rejected");
        server.verify();
    }

    @Test
    void createSubscription_billingServiceAnswers400_isNotFlattenedIntoBadGateway() {
        // Second 4xx, deliberately a different one: the classification must key on the 4xx family,
        // not on a single hardcoded status.
        server.expect(requestTo(SUBSCRIPTIONS_URI))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        BillingServiceException exception = catchThrowableOfType(
                () -> adapter.createSubscription(TENANT_ID, "owner@example.com", "Demo Salon"),
                BillingServiceException.class);

        assertThat(exception.getHttpStatus())
                .isNotEqualTo(HttpStatus.BAD_GATEWAY)
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        server.verify();
    }

    @Test
    void createSubscription_billingServiceAnswers500_stillSurfacesAsBadGateway() {
        // The other half of the property: a genuinely broken dependency must keep its 502.
        server.expect(requestTo(SUBSCRIPTIONS_URI))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        BillingServiceException exception = catchThrowableOfType(
                () -> adapter.createSubscription(TENANT_ID, "owner@example.com", "Demo Salon"),
                BillingServiceException.class);

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exception.getErrorType()).isEqualTo("billing-service-error");
        server.verify();
    }

    @Test
    void createSubscription_billingServiceRefusesTheConnection_stillSurfacesAsBadGateway() {
        server.expect(requestTo(SUBSCRIPTIONS_URI))
                .andExpect(method(POST))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        BillingServiceException exception = catchThrowableOfType(
                () -> adapter.createSubscription(TENANT_ID, "owner@example.com", "Demo Salon"),
                BillingServiceException.class);

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exception.getErrorType()).isEqualTo("billing-service-error");
        server.verify();
    }

    @Test
    void createSubscription_restClientFailureThatIsNeitherHttpStatusNorNetwork_isCaughtByTheSafetyNet() {
        // Stands in for the RestClientException subtypes the two specific catches do NOT cover —
        // UnknownContentTypeException from an edge/proxy error page, a body cut short mid-stream,
        // a DTO shape mismatch during a rolling deploy. Deleting the final
        // `catch (RestClientException)` is the exact change that once shipped a production 500
        // from this package, so it is pinned here on its own: the specific catches CLASSIFY, they
        // never replace the safety net.
        server.expect(requestTo(SUBSCRIPTIONS_URI))
                .andExpect(method(POST))
                .andRespond(request -> {
                    throw new RestClientException("unreadable response");
                });

        BillingServiceException exception = catchThrowableOfType(
                () -> adapter.createSubscription(TENANT_ID, "owner@example.com", "Demo Salon"),
                BillingServiceException.class);

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        server.verify();
    }
}
