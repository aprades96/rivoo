package com.rivoo.billing.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.billing.domain.model.PlanName;
import com.rivoo.billing.domain.model.SubscriptionPlan;
import com.rivoo.billing.domain.port.out.PlanPersistencePort;
import com.rivoo.billing.infrastructure.adapter.out.persistence.repository.SubscriptionPlanJpaRepository;
import com.rivoo.billing.infrastructure.mapper.PlanPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PlanPersistenceAdapter implements PlanPersistencePort {

    private final SubscriptionPlanJpaRepository repository;
    private final PlanPersistenceMapper mapper;

    @Override
    public Optional<SubscriptionPlan> findByName(PlanName name) {
        return repository.findByName(name).map(mapper::toDomain);
    }

    @Override
    public Optional<SubscriptionPlan> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<SubscriptionPlan> findAllActive() {
        return repository.findByActiveTrue().stream()
                .map(mapper::toDomain)
                .toList();
    }
}
