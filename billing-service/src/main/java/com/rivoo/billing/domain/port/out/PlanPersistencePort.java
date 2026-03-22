package com.rivoo.billing.domain.port.out;

import com.rivoo.billing.domain.model.PlanName;
import com.rivoo.billing.domain.model.SubscriptionPlan;

import java.util.List;
import java.util.Optional;

public interface PlanPersistencePort {

    Optional<SubscriptionPlan> findByName(PlanName name);

    Optional<SubscriptionPlan> findById(Long id);

    List<SubscriptionPlan> findAllActive();
}
