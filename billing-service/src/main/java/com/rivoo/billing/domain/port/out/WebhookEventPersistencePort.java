package com.rivoo.billing.domain.port.out;

import com.rivoo.billing.domain.model.WebhookEvent;

public interface WebhookEventPersistencePort {

    WebhookEvent save(WebhookEvent webhookEvent);

    boolean existsByStripeEventId(String stripeEventId);
}
