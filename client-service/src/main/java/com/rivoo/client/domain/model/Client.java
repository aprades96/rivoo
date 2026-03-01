package com.rivoo.client.domain.model;

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
public class Client {

    private Long id;
    private String externalId;
    private String tenantId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private Gender gender;
    private ClientSource source;
    private String notes;
    private int totalVisits;
    private Instant lastVisitAt;
    private Instant gdprConsentAt;
    private Instant gdprAnonymizedAt;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isAnonymized() {
        return gdprAnonymizedAt != null;
    }

    public void anonymize() {
        this.firstName = "ANONYMIZED";
        this.lastName = "CLIENT";
        this.email = null;
        this.phone = null;
        this.notes = null;
        this.gender = null;
        this.gdprAnonymizedAt = Instant.now();
        this.active = false;
    }
}
