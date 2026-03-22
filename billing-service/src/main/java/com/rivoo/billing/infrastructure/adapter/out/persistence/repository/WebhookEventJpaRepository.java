package com.rivoo.billing.infrastructure.adapter.out.persistence.repository;

import com.rivoo.billing.infrastructure.adapter.out.persistence.entity.WebhookEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventJpaRepository extends JpaRepository<WebhookEventJpaEntity, Long> {

    boolean existsByStripeEventId(String stripeEventId);
}
