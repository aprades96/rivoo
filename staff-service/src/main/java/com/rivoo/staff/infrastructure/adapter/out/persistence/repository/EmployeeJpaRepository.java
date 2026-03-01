package com.rivoo.staff.infrastructure.adapter.out.persistence.repository;

import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.EmployeeJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmployeeJpaRepository extends JpaRepository<EmployeeJpaEntity, Long> {

    Optional<EmployeeJpaEntity> findByExternalId(String externalId);

    Optional<EmployeeJpaEntity> findByKeycloakUserId(String keycloakUserId);

    Page<EmployeeJpaEntity> findByActiveTrue(Pageable pageable);

    @Query("SELECT COUNT(e) FROM EmployeeJpaEntity e WHERE e.tenantId = :tenantId AND e.active = true")
    long countActiveByTenantId(@Param("tenantId") String tenantId);
}
