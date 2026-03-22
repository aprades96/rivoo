package com.rivoo.billing.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.billing.domain.model.PlanLimit;
import com.rivoo.billing.domain.port.out.PlanLimitPersistencePort;
import com.rivoo.billing.infrastructure.adapter.out.persistence.repository.PlanLimitJpaRepository;
import com.rivoo.billing.infrastructure.mapper.PlanLimitPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PlanLimitPersistenceAdapter implements PlanLimitPersistencePort {

    private final PlanLimitJpaRepository repository;
    private final PlanLimitPersistenceMapper mapper;

    @Override
    public List<PlanLimit> findByPlanId(Long planId) {
        return repository.findByPlanId(planId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<PlanLimit> findByPlanIdAndKey(Long planId, String key) {
        return repository.findByPlanIdAndLimitKey(planId, key).map(mapper::toDomain);
    }
}
