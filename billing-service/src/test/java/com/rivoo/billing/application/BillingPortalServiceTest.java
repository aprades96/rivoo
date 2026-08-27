package com.rivoo.billing.application;

import com.rivoo.billing.application.dto.PortalResponse;
import com.rivoo.billing.domain.exception.StripeCustomerNotLinkedException;
import com.rivoo.billing.domain.exception.SubscriptionNotFoundException;
import com.rivoo.billing.domain.model.PlanName;
import com.rivoo.billing.domain.model.Subscription;
import com.rivoo.billing.domain.model.SubscriptionStatus;
import com.rivoo.billing.domain.port.out.StripePort;
import com.rivoo.billing.domain.port.out.SubscriptionPersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingPortalServiceTest {

    @Mock
    private SubscriptionPersistencePort subscriptionPersistencePort;

    @Mock
    private StripePort stripePort;

    private BillingPortalService billingPortalService;

    private static final String TENANT_ID = "sal_tenant-001";
    private static final String STRIPE_CUSTOMER_ID = "cus_stripe123";
    private static final String RETURN_URL = "http://localhost:3000/settings/billing";
    private static final String PORTAL_URL = "https://billing.stripe.com/mock-portal/abc";

    @BeforeEach
    void setUp() {
        billingPortalService = new BillingPortalService(
                subscriptionPersistencePort, stripePort, RETURN_URL);
    }

    @Test
    void createPortalSession_returnsPortalUrlUnderTheFieldNameTheFrontendReads() {
        when(subscriptionPersistencePort.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(buildSubscription(STRIPE_CUSTOMER_ID)));
        when(stripePort.createBillingPortalSession(STRIPE_CUSTOMER_ID, RETURN_URL)).thenReturn(PORTAL_URL);

        PortalResponse response = billingPortalService.createPortalSession(TENANT_ID);

        // billing.ts reads `data.url`; renaming this component breaks the redirect silently.
        assertThat(response.url()).isEqualTo(PORTAL_URL);
    }

    @Test
    void createPortalSession_passesConfiguredReturnUrlToStripe() {
        when(subscriptionPersistencePort.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(buildSubscription(STRIPE_CUSTOMER_ID)));
        when(stripePort.createBillingPortalSession(anyString(), anyString())).thenReturn(PORTAL_URL);

        billingPortalService.createPortalSession(TENANT_ID);

        verify(stripePort).createBillingPortalSession(STRIPE_CUSTOMER_ID, RETURN_URL);
    }

    @Test
    void createPortalSession_noSubscriptionForTenant_throwsSubscriptionNotFoundException() {
        when(subscriptionPersistencePort.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingPortalService.createPortalSession(TENANT_ID))
                .isInstanceOf(SubscriptionNotFoundException.class)
                .extracting(ex -> ((SubscriptionNotFoundException) ex).getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(stripePort, never()).createBillingPortalSession(any(), any());
    }

    @Test
    void createPortalSession_subscriptionWithoutStripeCustomer_throwsMappedTo422() {
        when(subscriptionPersistencePort.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(buildSubscription(null)));

        // GlobalExceptionHandler derives the HTTP status from RivooException#getHttpStatus,
        // so asserting the status here is asserting the actual API contract: 422, not 500.
        assertThatThrownBy(() -> billingPortalService.createPortalSession(TENANT_ID))
                .isInstanceOf(StripeCustomerNotLinkedException.class)
                .extracting(ex -> ((StripeCustomerNotLinkedException) ex).getHttpStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        verify(stripePort, never()).createBillingPortalSession(any(), any());
    }

    private Subscription buildSubscription(String stripeCustomerId) {
        return Subscription.builder()
                .id(10L)
                .externalId("sub_abc123")
                .tenantId(TENANT_ID)
                .planId(2L)
                .planName(PlanName.PREMIUM)
                .status(SubscriptionStatus.ACTIVE)
                .stripeCustomerId(stripeCustomerId)
                .build();
    }
}
