package com.rivoo.client.infrastructure.adapter.out.persistence.repository;

import com.rivoo.client.infrastructure.adapter.out.persistence.entity.ClientJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClientJpaRepository extends JpaRepository<ClientJpaEntity, Long> {

    Optional<ClientJpaEntity> findByExternalId(String externalId);

    Optional<ClientJpaEntity> findByTenantIdAndEmail(String tenantId, String email);

    Optional<ClientJpaEntity> findByTenantIdAndPhone(String tenantId, String phone);

    Optional<ClientJpaEntity> findByExternalIdAndTenantId(String externalId, String tenantId);

    boolean existsByTenantIdAndEmail(String tenantId, String email);

    /**
     * Lists clients, optionally filtered by a case-insensitive substring match on
     * first name, last name, phone or email. A {@code null}, empty or
     * whitespace-only {@code search} returns everything.
     * <p>
     * The order is fixed here — {@code lastVisitAt DESC, createdAt DESC} — rather
     * than delegated to {@code Pageable}, so every consumer of this query sees a
     * consistent "most recent client first" ordering regardless of what sort
     * parameters (if any) it sends. In MySQL, NULL sorts as the lowest value, so
     * clients with no {@code lastVisitAt} yet naturally end up last.
     * <p>
     * JPQL (not native SQL): Hibernate's {@code tenantFilter} — activated by
     * {@code TenantFilterAspect} — only applies to HQL/JPQL and Criteria queries,
     * never to native SQL, so tenant isolation depends on staying in JPQL here.
     */
    @Query("""
            SELECT c FROM ClientJpaEntity c
            WHERE (:search IS NULL OR TRIM(:search) = ''
                OR LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY c.lastVisitAt DESC, c.createdAt DESC
            """)
    Page<ClientJpaEntity> search(@Param("search") String search, Pageable pageable);
}
