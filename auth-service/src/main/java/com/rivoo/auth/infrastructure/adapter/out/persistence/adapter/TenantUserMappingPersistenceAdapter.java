package com.rivoo.auth.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.auth.domain.model.TenantUserMapping;
import com.rivoo.auth.domain.port.out.TenantUserMappingPort;
import com.rivoo.auth.infrastructure.adapter.out.persistence.entity.TenantUserMappingJpaEntity;
import com.rivoo.auth.infrastructure.adapter.out.persistence.repository.TenantUserMappingJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TenantUserMappingPersistenceAdapter implements TenantUserMappingPort {

    private final TenantUserMappingJpaRepository repository;

    public TenantUserMappingPersistenceAdapter(TenantUserMappingJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public TenantUserMapping save(TenantUserMapping mapping) {
        TenantUserMappingJpaEntity entity = toJpaEntity(mapping);
        TenantUserMappingJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public List<TenantUserMapping> findByTenantId(String tenantId) {
        return repository.findByTenantId(tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void updateActiveStatusByTenantId(String tenantId, boolean active) {
        repository.updateActiveStatusByTenantId(tenantId, active);
    }

    private TenantUserMappingJpaEntity toJpaEntity(TenantUserMapping mapping) {
        TenantUserMappingJpaEntity entity = new TenantUserMappingJpaEntity();
        entity.setTenantId(mapping.getTenantId());
        entity.setKeycloakUserId(mapping.getKeycloakUserId());
        entity.setRole(mapping.getRole());
        entity.setActive(mapping.isActive());
        return entity;
    }

    private TenantUserMapping toDomain(TenantUserMappingJpaEntity entity) {
        TenantUserMapping mapping = new TenantUserMapping();
        mapping.setId(entity.getId());
        mapping.setTenantId(entity.getTenantId());
        mapping.setKeycloakUserId(entity.getKeycloakUserId());
        mapping.setRole(entity.getRole());
        mapping.setActive(entity.isActive());
        mapping.setCreatedAt(entity.getCreatedAt());
        mapping.setUpdatedAt(entity.getUpdatedAt());
        return mapping;
    }
}
