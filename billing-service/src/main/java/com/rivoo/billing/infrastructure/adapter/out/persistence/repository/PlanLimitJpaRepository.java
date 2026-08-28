package com.rivoo.billing.infrastructure.adapter.out.persistence.repository;

import com.rivoo.billing.infrastructure.adapter.out.persistence.entity.PlanLimitJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlanLimitJpaRepository extends JpaRepository<PlanLimitJpaEntity, Long> {

    List<PlanLimitJpaEntity> findByPlanId(Long planId);

    List<PlanLimitJpaEntity> findByPlanIdIn(Collection<Long> planIds);

    Optional<PlanLimitJpaEntity> findByPlanIdAndLimitKey(Long planId, String limitKey);
}
