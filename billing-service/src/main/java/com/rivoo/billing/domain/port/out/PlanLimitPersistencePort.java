package com.rivoo.billing.domain.port.out;

import com.rivoo.billing.domain.model.PlanLimit;

import java.util.List;
import java.util.Optional;

public interface PlanLimitPersistencePort {

    List<PlanLimit> findByPlanId(Long planId);

    Optional<PlanLimit> findByPlanIdAndKey(Long planId, String key);
}
