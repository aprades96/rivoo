package com.rivoo.staff.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.staff.domain.model.Employee;
import com.rivoo.staff.domain.port.out.EmployeePersistencePort;
import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.EmployeeJpaEntity;
import com.rivoo.staff.infrastructure.adapter.out.persistence.repository.EmployeeJpaRepository;
import com.rivoo.staff.infrastructure.mapper.EmployeePersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EmployeePersistenceAdapter implements EmployeePersistencePort {

    private final EmployeeJpaRepository repository;
    private final EmployeePersistenceMapper mapper;

    @Override
    public Employee save(Employee employee) {
        EmployeeJpaEntity entity = mapper.toJpaEntity(employee);
        EmployeeJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Employee> findByExternalId(String externalId) {
        return repository.findByExternalId(externalId).map(mapper::toDomain);
    }

    @Override
    public Optional<Employee> findByKeycloakUserId(String keycloakUserId) {
        return repository.findByKeycloakUserId(keycloakUserId).map(mapper::toDomain);
    }

    @Override
    public Page<Employee> findAllActive(Pageable pageable) {
        return repository.findByActiveTrue(pageable).map(mapper::toDomain);
    }

    @Override
    public long countActiveByTenantId(String tenantId) {
        return repository.countActiveByTenantId(tenantId);
    }
}
