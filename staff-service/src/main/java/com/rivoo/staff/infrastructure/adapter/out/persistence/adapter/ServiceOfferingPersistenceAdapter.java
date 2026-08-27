package com.rivoo.staff.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.staff.domain.model.ServiceOffering;
import com.rivoo.staff.domain.port.out.ServiceOfferingPersistencePort;
import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.ServiceOfferingJpaEntity;
import com.rivoo.staff.infrastructure.adapter.out.persistence.repository.ServiceOfferingJpaRepository;
import com.rivoo.staff.infrastructure.mapper.ServiceOfferingPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ServiceOfferingPersistenceAdapter implements ServiceOfferingPersistencePort {

    private final ServiceOfferingJpaRepository repository;
    private final ServiceOfferingPersistenceMapper mapper;

    @Override
    public ServiceOffering save(ServiceOffering service) {
        ServiceOfferingJpaEntity entity = mapper.toJpaEntity(service);
        ServiceOfferingJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ServiceOffering> findByExternalId(String externalId) {
        return repository.findByExternalId(externalId).map(mapper::toDomain);
    }

    @Override
    public Page<ServiceOffering> findAllActive(Pageable pageable) {
        return repository.findByActiveTrue(pageable).map(mapper::toDomain);
    }

    @Override
    public boolean existsByNameAndTenantId(String name, String tenantId) {
        return repository.existsByNameAndTenantId(name, tenantId);
    }

    @Override
    public List<ServiceOffering> findAllActiveByTenantId(String tenantId) {
        return repository.findByTenantIdAndActiveTrue(tenantId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
