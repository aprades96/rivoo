package com.rivoo.salon.infrastructure.adapter.out.persistence.repository;

import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.infrastructure.adapter.out.persistence.entity.SalonJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SalonJpaRepository extends JpaRepository<SalonJpaEntity, Long> {

    Optional<SalonJpaEntity> findByTenantId(String tenantId);

    @Query("SELECT s FROM SalonJpaEntity s WHERE s.slug = :slug")
    Optional<SalonJpaEntity> findBySlug(@Param("slug") String slug);

    boolean existsBySlug(String slug);

    boolean existsByEmail(String email);

    Page<SalonJpaEntity> findAll(Pageable pageable);

    @Query("SELECT s FROM SalonJpaEntity s WHERE s.status = :status AND s.createdAt < :before")
    List<SalonJpaEntity> findByStatusAndCreatedAtBefore(
            @Param("status") SalonStatus status,
            @Param("before") Instant before);

    /**
     * Compare-and-set on {@code status}. The {@code expectedStatus} predicate is the whole point:
     * it makes the check and the write one statement, so concurrent callers cannot both observe the
     * old status and both act on it. Returns the number of rows the database actually changed.
     * <p>
     * {@code updatedAt} is set explicitly because a bulk JPQL update bypasses the {@code @PreUpdate}
     * callback that normally maintains it. {@code tenant_id} is in the predicate, so this does not
     * depend on the Hibernate tenant {@code @Filter} - which bulk update statements ignore anyway.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE SalonJpaEntity s SET s.status = :newStatus, s.updatedAt = :now "
            + "WHERE s.tenantId = :tenantId AND s.status = :expectedStatus")
    int updateStatusIfCurrentlyIs(@Param("tenantId") String tenantId,
                                  @Param("expectedStatus") SalonStatus expectedStatus,
                                  @Param("newStatus") SalonStatus newStatus,
                                  @Param("now") Instant now);

    /**
     * Compare-and-set on {@code onboardingCompletedAt}: writes it only while it is still
     * {@code null}, so a double click, two tabs, or a retried call all collapse into the same
     * single write and none of them can overwrite the timestamp a previous call already set.
     * <p>
     * {@code updatedAt} is set explicitly because a bulk JPQL update bypasses the
     * {@code @PreUpdate} callback that normally maintains it. {@code tenant_id} is in the
     * predicate, so this does not depend on the Hibernate tenant {@code @Filter} - which bulk
     * update statements ignore anyway.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE SalonJpaEntity s SET s.onboardingCompletedAt = :now, s.updatedAt = :now "
            + "WHERE s.tenantId = :tenantId AND s.onboardingCompletedAt IS NULL")
    int markOnboardingCompletedIfPending(@Param("tenantId") String tenantId, @Param("now") Instant now);
}
