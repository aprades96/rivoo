package com.rivoo.staff.infrastructure.adapter.out.persistence.repository;

import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.EmployeeJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeJpaRepository extends JpaRepository<EmployeeJpaEntity, Long> {

    Optional<EmployeeJpaEntity> findByExternalId(String externalId);

    Optional<EmployeeJpaEntity> findByKeycloakUserId(String keycloakUserId);

    /**
     * Lists employees, optionally including inactive ones. Ordering is left to
     * {@code Pageable} — {@code EmployeeService.list} fills in a deterministic
     * default sort when the caller sends none.
     * <p>
     * JPQL (not native SQL): Hibernate's {@code tenantFilter} — activated by
     * {@code TenantFilterAspect} — only applies to HQL/JPQL and Criteria queries,
     * never to native SQL, so tenant isolation depends on staying in JPQL here
     * (same warning as {@code ClientJpaRepository}).
     */
    @Query("""
            SELECT e FROM EmployeeJpaEntity e
            WHERE (:includeInactive = true OR e.active = true)
            """)
    Page<EmployeeJpaEntity> search(@Param("includeInactive") boolean includeInactive, Pageable pageable);

    @Query("SELECT COUNT(e) FROM EmployeeJpaEntity e WHERE e.tenantId = :tenantId AND e.active = true")
    long countActiveByTenantId(@Param("tenantId") String tenantId);

    List<EmployeeJpaEntity> findByTenantIdAndActiveTrue(String tenantId);
}
