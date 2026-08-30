package com.rivoo.staff.domain.port.out;

import com.rivoo.staff.domain.model.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface EmployeePersistencePort {

    Employee save(Employee employee);

    Optional<Employee> findByExternalId(String externalId);

    Optional<Employee> findByKeycloakUserId(String keycloakUserId);

    Page<Employee> search(boolean includeInactive, Pageable pageable);

    long countActiveByTenantId(String tenantId);

    List<Employee> findAllActiveByTenantId(String tenantId);
}
