package com.rivoo.billing.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

    private Long id;
    private String stripeEventId;
    private String eventType;
    private Instant processedAt;
    private String payload;
}
