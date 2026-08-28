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
     * Written deliberately, and exclusively, by {@code SalonService.completeOnboarding} through
     * the compare-and-set {@code SalonPersistencePort.markOnboardingCompleted} - but it can also
     * be overwritten by two unrelated read-modify-save flows that load the whole aggregate:
     * {@code SalonService.update} (PUT /api/v1/salons/me) and {@code SalonService.updateStatus}.
     * Either one loading this salon before the CAS commits, then saving, will merge back the
     * {@code null} it read and undo the completion. The window is narrow and this race is known
     * and accepted - it is not an invariant of the code that this field is touched in only one
     * place.
     */
    private Instant onboardingCompletedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
