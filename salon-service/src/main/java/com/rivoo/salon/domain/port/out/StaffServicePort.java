package com.rivoo.salon.domain.port.out;

import java.math.BigDecimal;
import java.util.List;

public interface StaffServicePort {

    record EmployeePublicInfo(String id, String firstName, String lastName,
                              String jobTitle, List<String> serviceIds) {}

    record ServicePublicInfo(String id, String name, String description,
                             int durationMinutes, BigDecimal price, String currency) {}

    List<EmployeePublicInfo> getPublicEmployees(String tenantId);

    List<ServicePublicInfo> getPublicServices(String tenantId);
}
