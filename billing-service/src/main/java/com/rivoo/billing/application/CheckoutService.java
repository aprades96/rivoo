package com.rivoo.billing.application;

import com.rivoo.billing.application.dto.CheckoutRequest;
import com.rivoo.billing.application.dto.CheckoutResponse;
import com.rivoo.billing.domain.exception.PlanNotFoundException;
import com.rivoo.billing.domain.exception.SubscriptionNotFoundException;
import com.rivoo.billing.domain.model.PlanName;
import com.rivoo.billing.domain.model.Subscription;
import com.rivoo.billing.domain.model.SubscriptionPlan;
import com.rivoo.billing.domain.port.in.CheckoutUseCase;
import com.rivoo.billing.domain.port.out.PlanPersistencePort;
import com.rivoo.billing.domain.port.out.StripePort;
import com.rivoo.billing.domain.port.out.SubscriptionPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService implements CheckoutUseCase {

    private final SubscriptionPersistencePort subscriptionPersistencePort;
    private final PlanPersistencePort planPersistencePort;
    private final StripePort stripePort;

    @Override
    @Transactional(readOnly = true)
    public CheckoutResponse createCheckoutSession(String tenantId, CheckoutRequest request) {
        Subscription subscription = subscriptionPersistencePort.findByTenantId(tenantId)
                .orElseThrow(() -> new SubscriptionNotFoundException(tenantId));

        PlanName targetPlan = PlanName.valueOf(request.planName());
        SubscriptionPlan plan = planPersistencePort.findByName(targetPlan)
                .orElseThrow(() -> new PlanNotFoundException(request.planName()));

        String successUrl = request.successUrl() != null ? request.successUrl() : "http://localhost:3000/billing/success";
        String cancelUrl = request.cancelUrl() != null ? request.cancelUrl() : "http://localhost:3000/billing/cancel";

        String checkoutUrl = stripePort.createCheckoutSession(
                subscription.getStripeCustomerId(),
                plan.getStripeMonthlyPriceId(),
                successUrl, cancelUrl);

        log.atInfo()
                .addKeyValue("targetPlan", request.planName())
                .log("Checkout session created");

        return new CheckoutResponse(checkoutUrl, "mock-session-id");
    }
}
