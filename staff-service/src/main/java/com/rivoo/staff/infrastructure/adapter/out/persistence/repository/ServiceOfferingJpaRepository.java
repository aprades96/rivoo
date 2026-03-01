package com.rivoo.staff.infrastructure.adapter.out.persistence.repository;

import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.ServiceOfferingJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceOfferingJpaRepository extends JpaRepository<ServiceOfferingJpaEntity, Long> {

    Optional<ServiceOfferingJpaEntity> findByExternalId(String externalId);

    Page<ServiceOfferingJpaEntity> findByActiveTrue(Pageable pageable);

    boolean existsByNameAndTenantId(String name, String tenantId);
}
