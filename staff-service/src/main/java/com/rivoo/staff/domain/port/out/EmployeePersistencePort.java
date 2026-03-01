package com.rivoo.staff.domain.port.out;

import com.rivoo.staff.domain.model.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface EmployeePersistencePort {

    Employee save(Employee employee);

    Optional<Employee> findByExternalId(String externalId);

    Optional<Employee> findByKeycloakUserId(String keycloakUserId);

    Page<Employee> findAllActive(Pageable pageable);

    long countActiveByTenantId(String tenantId);
}
