package com.rivoo.billing.domain.port.out;

import com.rivoo.billing.domain.model.PlanLimit;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlanLimitPersistencePort {

    List<PlanLimit> findByPlanId(Long planId);

    /**
     * Rows for several plans in a single query. Exists so the plan catalogue does not
     * issue one {@code findByPlanId} per plan.
     */
    List<PlanLimit> findByPlanIds(Collection<Long> planIds);

    Optional<PlanLimit> findByPlanIdAndKey(Long planId, String key);
}
