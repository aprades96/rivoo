package com.rivoo.staff.domain.model;

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
public class Employee {

    private Long id;
    private String externalId;
    private String tenantId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String jobTitle;
    private String colorHex;
    private EmployeeRole role;
    private String keycloakUserId;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
