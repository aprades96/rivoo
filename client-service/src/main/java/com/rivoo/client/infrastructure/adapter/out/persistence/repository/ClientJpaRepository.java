package com.rivoo.client.infrastructure.adapter.out.persistence.repository;

import com.rivoo.client.infrastructure.adapter.out.persistence.entity.ClientJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientJpaRepository extends JpaRepository<ClientJpaEntity, Long> {

    Optional<ClientJpaEntity> findByExternalId(String externalId);

    Optional<ClientJpaEntity> findByTenantIdAndEmail(String tenantId, String email);

    Optional<ClientJpaEntity> findByTenantIdAndPhone(String tenantId, String phone);

    Optional<ClientJpaEntity> findByExternalIdAndTenantId(String externalId, String tenantId);

    boolean existsByTenantIdAndEmail(String tenantId, String email);
}
