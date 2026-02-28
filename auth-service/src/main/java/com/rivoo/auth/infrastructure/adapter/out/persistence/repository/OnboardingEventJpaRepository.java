package com.rivoo.auth.infrastructure.adapter.out.persistence.repository;

import com.rivoo.auth.infrastructure.adapter.out.persistence.entity.OnboardingEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingEventJpaRepository extends JpaRepository<OnboardingEventJpaEntity, Long> {
}
