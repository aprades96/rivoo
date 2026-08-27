package com.rivoo.billing.application;

import com.rivoo.billing.application.dto.PortalResponse;
import com.rivoo.billing.domain.exception.StripeCustomerNotLinkedException;
import com.rivoo.billing.domain.exception.SubscriptionNotFoundException;
import com.rivoo.billing.domain.model.Subscription;
import com.rivoo.billing.domain.port.in.BillingPortalUseCase;
import com.rivoo.billing.domain.port.out.StripePort;
import com.rivoo.billing.domain.port.out.SubscriptionPersistencePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class BillingPortalService implements BillingPortalUseCase {

    private final SubscriptionPersistencePort subscriptionPersistencePort;
    private final StripePort stripePort;
    private final String portalReturnUrl;

    public BillingPortalService(SubscriptionPersistencePort subscriptionPersistencePort,
                                StripePort stripePort,
                                // Inline default on purpose: this URL is not a secret and a missing
                                // property must never keep billing-service from starting. Every
                                // profile still sets it explicitly.
                                @Value("${rivoo.billing.portal-return-url:http://localhost:3000/settings/billing}")
                                String portalReturnUrl) {
        this.subscriptionPersistencePort = subscriptionPersistencePort;
        this.stripePort = stripePort;
        this.portalReturnUrl = portalReturnUrl;
    }

    @Override
    @Transactional(readOnly = true)
    public PortalResponse createPortalSession(String tenantId) {
        Subscription subscription = subscriptionPersistencePort.findByTenantId(tenantId)
                .orElseThrow(() -> new SubscriptionNotFoundException(tenantId));

        // hasText, not != null: stripe_customer_id is VARCHAR(100) NULL, so the column
        // also permits '' and whitespace. A blank id passes a null check and reaches
        // Stripe, which rejects it — surfacing as a generic 500 instead of this 422.
        String stripeCustomerId = subscription.getStripeCustomerId();
        if (!StringUtils.hasText(stripeCustomerId)) {
            throw new StripeCustomerNotLinkedException(tenantId);
        }

        String portalUrl = stripePort.createBillingPortalSession(stripeCustomerId, portalReturnUrl);

        log.atInfo()
                .addKeyValue("stripeCustomerId", stripeCustomerId)
                .addKeyValue("returnUrl", portalReturnUrl)
                .log("Billing portal session created");

        return new PortalResponse(portalUrl);
    }
}
