package com.rivoo.salon.infrastructure.adapter.out.persistence.repository;

import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.infrastructure.adapter.out.persistence.entity.SalonJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
