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
    private String logoUrl;
    private String primaryColor;
    private String addressStreet;
    private String addressCity;
    private String addressPostalCode;
    private String timezone;
    private String currency;
    private SubscriptionPlan subscriptionPlan;
    private SalonStatus status;
    /**
     * Written exclusively by {@code SalonService.completeOnboarding} through the compare-and-set
     * {@code SalonPersistencePort.markOnboardingCompleted}. The two unrelated read-modify-save flows
     * that load the whole aggregate - {@code SalonService.update} (PUT /api/v1/salons/me) and
     * {@code SalonService.updateStatus} - used to be able to race the compare-and-set and merge a
     * stale {@code null} back over a timestamp it had just committed. That is now closed at the JPA
     * mapping level: {@code SalonJpaEntity.onboardingCompletedAt} is {@code updatable = false}, so no
     * {@code merge()} of this aggregate can write this column any more - the bulk JPQL update behind
     * {@code markOnboardingCompleted} remains the only writer, since it translates to SQL directly
     * and is not subject to entity-level column metadata.
     */
    private Instant onboardingCompletedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
