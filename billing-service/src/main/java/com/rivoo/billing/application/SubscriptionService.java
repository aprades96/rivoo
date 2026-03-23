package com.rivoo.billing.application;

import com.rivoo.billing.application.dto.CreateSubscriptionRequest;
import com.rivoo.billing.application.dto.PlanResponse;
import com.rivoo.billing.application.dto.SubscriptionResponse;
import com.rivoo.billing.domain.exception.DuplicateSubscriptionException;
import com.rivoo.billing.domain.exception.PlanNotFoundException;
import com.rivoo.billing.domain.exception.SubscriptionNotFoundException;
import com.rivoo.billing.domain.model.PlanName;
import com.rivoo.billing.domain.model.Subscription;
import com.rivoo.billing.domain.model.SubscriptionPlan;
import com.rivoo.billing.domain.model.SubscriptionStatus;
import com.rivoo.billing.domain.port.in.CreateSubscriptionUseCase;
import com.rivoo.billing.domain.port.in.GetSubscriptionUseCase;
import com.rivoo.billing.domain.port.in.ListPlansUseCase;
import com.rivoo.billing.domain.port.in.UpdateSubscriptionStatusUseCase;
import com.rivoo.billing.domain.port.out.AuthServicePort;
import com.rivoo.billing.domain.port.out.PlanPersistencePort;
import com.rivoo.billing.domain.port.out.StripePort;
import com.rivoo.billing.domain.port.out.SubscriptionPersistencePort;
import com.rivoo.common.util.ExternalIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService implements CreateSubscriptionUseCase, GetSubscriptionUseCase,
        ListPlansUseCase, UpdateSubscriptionStatusUseCase {

    private final SubscriptionPersistencePort subscriptionPersistencePort;
    private final PlanPersistencePort planPersistencePort;
    private final StripePort stripePort;
    private final AuthServicePort authServicePort;
    private final PlanLimitsService planLimitsService;

    @Override
    @Transactional
    public SubscriptionResponse create(CreateSubscriptionRequest request) {
        if (subscriptionPersistencePort.existsByTenantId(request.tenantId())) {
            throw new DuplicateSubscriptionException(request.tenantId());
        }

        SubscriptionPlan freeTrial = planPersistencePort.findByName(PlanName.FREE_TRIAL)
                .orElseThrow(() -> new PlanNotFoundException("FREE_TRIAL"));

        // Create Stripe Customer (stub returns mock ID)
        String stripeCustomerId = stripePort.createCustomer(
                request.tenantId(), request.ownerEmail(), request.salonName());

        Instant now = Instant.now();
        Subscription subscription = Subscription.builder()
                .externalId(ExternalIdGenerator.generate("sub"))
                .tenantId(request.tenantId())
                .planId(freeTrial.getId())
                .planName(freeTrial.getName())
                .status(SubscriptionStatus.TRIALING)
                .stripeCustomerId(stripeCustomerId)
                .trialStart(now)
                .trialEnd(now.plus(freeTrial.getTrialDays(), ChronoUnit.DAYS))
                .build();

        Subscription saved = subscriptionPersistencePort.save(subscription);

        log.atInfo()
                .addKeyValue("subscriptionId", saved.getExternalId())
                .addKeyValue("stripeCustomerId", stripeCustomerId)
                .log("Subscription created (FREE_TRIAL)");

        return toResponse(saved, freeTrial);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse getByTenantId(String tenantId) {
        Subscription subscription = findSubscriptionOrThrow(tenantId);
        SubscriptionPlan plan = planPersistencePort.findById(subscription.getPlanId())
                .orElseThrow(() -> new PlanNotFoundException(String.valueOf(subscription.getPlanId())));
        return toResponse(subscription, plan);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponse> listActivePlans() {
        return planPersistencePort.findAllActive().stream()
                .map(plan -> new PlanResponse(
                        plan.getExternalId(),
                        plan.getName().name(),
                        plan.getDisplayName(),
                        plan.getMonthlyPrice(),
                        plan.getTrialDays()))
                .toList();
    }

    @Override
    @Transactional
    public SubscriptionResponse updateStatus(String tenantId, String newStatus) {
        Subscription subscription = findSubscriptionOrThrow(tenantId);
        SubscriptionStatus targetStatus = SubscriptionStatus.valueOf(newStatus);
        subscription.setStatus(targetStatus);
        Subscription saved = subscriptionPersistencePort.save(subscription);

        SubscriptionPlan plan = planPersistencePort.findById(saved.getPlanId())
                .orElseThrow(() -> new PlanNotFoundException(String.valueOf(saved.getPlanId())));

        log.atInfo()
                .addKeyValue("newStatus", newStatus)
                .log("Subscription status updated");

        return toResponse(saved, plan);
    }

    @Transactional
    public void upgradePlan(String tenantId, PlanName newPlanName) {
        Subscription subscription = findSubscriptionOrThrow(tenantId);
        SubscriptionPlan newPlan = planPersistencePort.findByName(newPlanName)
                .orElseThrow(() -> new PlanNotFoundException(newPlanName.name()));

        subscription.setPlanId(newPlan.getId());
        subscription.setPlanName(newPlan.getName());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(Instant.now());
        subscription.setCurrentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));
        subscriptionPersistencePort.save(subscription);

        // Sync to Keycloak
        try {
            authServicePort.updateTenantAttributes(tenantId,
                    Map.of("subscription_plan", newPlanName.name()));
        } catch (Exception e) {
            log.atWarn().setCause(e)
                    .log("Failed to sync plan to Keycloak — subscription updated anyway");
        }

        // Evict cache
        planLimitsService.evictCache(tenantId);

        log.atInfo()
                .addKeyValue("newPlan", newPlanName)
                .log("Subscription upgraded");
    }

    private Subscription findSubscriptionOrThrow(String tenantId) {
        return subscriptionPersistencePort.findByTenantId(tenantId)
                .orElseThrow(() -> new SubscriptionNotFoundException(tenantId));
    }

    private SubscriptionResponse toResponse(Subscription sub, SubscriptionPlan plan) {
        return new SubscriptionResponse(
                sub.getExternalId(), sub.getTenantId(),
                plan.getName().name(), plan.getDisplayName(), plan.getMonthlyPrice(),
                sub.getStatus().name(),
                sub.getTrialStart(), sub.getTrialEnd(),
                sub.getCurrentPeriodStart(), sub.getCurrentPeriodEnd(),
                sub.isCancelAtPeriodEnd(), sub.getCreatedAt());
    }
}
