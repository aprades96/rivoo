package com.rivoo.billing.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.rivoo.billing.application.dto.PlanLimitsResponse;
import com.rivoo.billing.domain.exception.SubscriptionNotFoundException;
import com.rivoo.billing.domain.model.PlanLimit;
import com.rivoo.billing.domain.model.PlanLimits;
import com.rivoo.billing.domain.model.PlanName;
import com.rivoo.billing.domain.model.Subscription;
import com.rivoo.billing.domain.model.SubscriptionPlan;
import com.rivoo.billing.domain.port.in.ManagePlanLimitsUseCase;
import com.rivoo.billing.domain.port.out.PlanLimitPersistencePort;
import com.rivoo.billing.domain.port.out.PlanPersistencePort;
import com.rivoo.billing.domain.port.out.SubscriptionPersistencePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class PlanLimitsService implements ManagePlanLimitsUseCase {

    private final SubscriptionPersistencePort subscriptionPersistencePort;
    private final PlanPersistencePort planPersistencePort;
    private final PlanLimitPersistencePort planLimitPersistencePort;

    private final Cache<String, PlanLimitsResponse> cache;

    public PlanLimitsService(SubscriptionPersistencePort subscriptionPersistencePort,
                             PlanPersistencePort planPersistencePort,
                             PlanLimitPersistencePort planLimitPersistencePort) {
        this.subscriptionPersistencePort = subscriptionPersistencePort;
        this.planPersistencePort = planPersistencePort;
        this.planLimitPersistencePort = planLimitPersistencePort;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(500)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PlanLimitsResponse getPlanLimits(String tenantId, boolean forWriteOperation) {
        if (!forWriteOperation) {
            PlanLimitsResponse cached = cache.getIfPresent(tenantId);
            if (cached != null) {
                log.atDebug().log("Plan limits cache hit");
                return cached;
            }
        }

        Subscription subscription = subscriptionPersistencePort.findByTenantId(tenantId)
                .orElseThrow(() -> new SubscriptionNotFoundException(tenantId));

        SubscriptionPlan plan = planPersistencePort.findById(subscription.getPlanId())
                .orElseThrow(() -> new SubscriptionNotFoundException(tenantId));

        List<PlanLimit> limits = planLimitPersistencePort.findByPlanId(plan.getId());

        PlanLimitsResponse response = buildResponse(plan.getName(), limits);
        cache.put(tenantId, response);

        log.atDebug().addKeyValue("plan", plan.getName()).log("Plan limits fetched from DB");
        return response;
    }

    /**
     * Enforcement-side defaults for a missing {@code plan_limits} row, unchanged from
     * before {@link PlanLimits} was extracted: a missing quota reads as {@code -1}
     * (unlimited) and a missing flag as {@code false}. That is permissive for quotas and
     * restrictive for features, and it is what appointment-service and staff-service have
     * always been served, so it is preserved verbatim here rather than "fixed" in passing.
     * <p>
     * The anonymous catalogue in {@code SubscriptionService#listActivePlans} deliberately
     * does NOT apply these defaults — it reports an absent row as {@code null}. Both go
     * through {@link PlanLimits#from}, so only the default policy differs, never the key
     * names or the int-to-boolean encoding.
     */
    private PlanLimitsResponse buildResponse(PlanName planName, List<PlanLimit> limits) {
        PlanLimits flattened = PlanLimits.from(limits);

        return new PlanLimitsResponse(
                planName.name(),
                Objects.requireNonNullElse(flattened.maxEmployees(), -1),
                Objects.requireNonNullElse(flattened.maxAppointmentsPerMonth(), -1),
                Objects.requireNonNullElse(flattened.emailRemindersEnabled(), false),
                Objects.requireNonNullElse(flattened.smsRemindersEnabled(), false));
    }

    public void evictCache(String tenantId) {
        cache.invalidate(tenantId);
    }
}
