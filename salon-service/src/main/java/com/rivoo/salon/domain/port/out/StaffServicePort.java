package com.rivoo.salon.domain.port.out;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StaffServicePort {

    record EmployeePublicInfo(String id, String firstName, String lastName,
                              String jobTitle, List<String> serviceIds) {}

    record ServicePublicInfo(String id, String name, String description,
                             int durationMinutes, BigDecimal price, String currency) {}

    // Optional.empty() means the call to staff-service failed (network error, 5xx,
    // unreadable body, etc.) and the catalogue could not be loaded: the caller must
    // treat this as "unknown", not as "no employees/services exist". A present
    // Optional — even one wrapping an empty list — means staff-service was reached
    // and answered normally: an empty list is then a legitimate state (e.g. a salon
    // that skipped the optional employees/services onboarding step), not a failure.
    // This distinction cannot be recovered once collapsed into a bare List, which is
    // exactly what the previous signature did.
    Optional<List<EmployeePublicInfo>> getPublicEmployees(String tenantId);

    Optional<List<ServicePublicInfo>> getPublicServices(String tenantId);
}
