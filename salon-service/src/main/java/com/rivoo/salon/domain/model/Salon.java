package com.rivoo.salon.domain.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Salon {

    private Long id;
    private String externalId;
    private String tenantId;
    private String name;
    private String slug;
    private String ownerUserId;
    private String email;
    private String phone;
    private String description;
    private String addressStreet;
    private String addressCity;
    private String addressPostalCode;
    private String timezone;
    private String currency;
    private SubscriptionPlan subscriptionPlan;
    private SalonStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
