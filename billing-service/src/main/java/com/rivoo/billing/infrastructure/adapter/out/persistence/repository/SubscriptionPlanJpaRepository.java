package com.rivoo.billing.infrastructure.adapter.out.persistence.repository;

import com.rivoo.billing.domain.model.PlanName;
import com.rivoo.billing.infrastructure.adapter.out.persistence.entity.SubscriptionPlanJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanJpaRepository extends JpaRepository<SubscriptionPlanJpaEntity, Long> {

    Optional<SubscriptionPlanJpaEntity> findByName(PlanName name);

    List<SubscriptionPlanJpaEntity> findByActiveTrue();
}
