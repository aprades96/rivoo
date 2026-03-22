package com.rivoo.billing.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.rivoo.billing.application.dto.PlanLimitsResponse;
import com.rivoo.billing.domain.exception.SubscriptionNotFoundException;
import com.rivoo.billing.domain.model.PlanLimit;
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
                log.atDebug().addKeyValue("tenantId", tenantId).log("Plan limits cache hit");
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

        log.atDebug().addKeyValue("tenantId", tenantId).addKeyValue("plan", plan.getName()).log("Plan limits fetched from DB");
        return response;
    }

    private PlanLimitsResponse buildResponse(PlanName planName, List<PlanLimit> limits) {
        int maxEmployees = getLimitValue(limits, "max_employees", -1);
        int maxAppointments = getLimitValue(limits, "max_appointments_per_month", -1);
        boolean emailReminders = getLimitValue(limits, "email_reminders_enabled", 0) == 1;
        boolean smsReminders = getLimitValue(limits, "sms_reminders_enabled", 0) == 1;

        return new PlanLimitsResponse(
                planName.name(), maxEmployees, maxAppointments, emailReminders, smsReminders);
    }

    private int getLimitValue(List<PlanLimit> limits, String key, int defaultValue) {
        return limits.stream()
                .filter(l -> key.equals(l.getLimitKey()))
                .map(PlanLimit::getLimitValue)
                .findFirst()
                .orElse(defaultValue);
    }

    public void evictCache(String tenantId) {
        cache.invalidate(tenantId);
    }
}
