package com.rivoo.appointment.infrastructure.adapter.out.persistence.repository;

import com.rivoo.appointment.domain.model.AppointmentStatus;
import com.rivoo.appointment.infrastructure.adapter.out.persistence.entity.AppointmentJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AppointmentJpaRepository extends JpaRepository<AppointmentJpaEntity, Long> {

    Optional<AppointmentJpaEntity> findByExternalId(String externalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AppointmentJpaEntity a WHERE a.externalId = :externalId")
    Optional<AppointmentJpaEntity> findByExternalIdForUpdate(@Param("externalId") String externalId);

    @Query("""
            SELECT a FROM AppointmentJpaEntity a
            WHERE a.tenantId = :tenantId
              AND a.employeeId = :employeeId
              AND a.startTime < :endTime
              AND a.endTime > :startTime
              AND a.status NOT IN :excludedStatuses
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AppointmentJpaEntity> findOverlappingForUpdate(
            @Param("tenantId") String tenantId,
            @Param("employeeId") String employeeId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("excludedStatuses") List<AppointmentStatus> excludedStatuses);

    @Query("""
            SELECT a FROM AppointmentJpaEntity a
            WHERE a.tenantId = :tenantId
              AND a.employeeId = :employeeId
              AND a.startTime >= :startOfDay
              AND a.startTime < :endOfDay
              AND a.status NOT IN :excludedStatuses
            ORDER BY a.startTime ASC
            """)
    List<AppointmentJpaEntity> findByEmployeeAndDateRange(
            @Param("tenantId") String tenantId,
            @Param("employeeId") String employeeId,
            @Param("startOfDay") Instant startOfDay,
            @Param("endOfDay") Instant endOfDay,
            @Param("excludedStatuses") List<AppointmentStatus> excludedStatuses);

    @Query("""
            SELECT a FROM AppointmentJpaEntity a
            WHERE (:employeeId IS NULL OR a.employeeId = :employeeId)
              AND (:startDate IS NULL OR a.startTime >= :startDate)
              AND (:endDate IS NULL OR a.startTime < :endDate)
              AND (:status IS NULL OR a.status = :status)
            ORDER BY a.startTime DESC
            """)
    Page<AppointmentJpaEntity> findByFilters(
            @Param("employeeId") String employeeId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("status") AppointmentStatus status,
            Pageable pageable);

    @Query("SELECT COUNT(a) FROM AppointmentJpaEntity a WHERE a.tenantId = :tenantId AND a.startTime >= :monthStart AND a.startTime < :monthEnd AND a.status NOT IN :excludedStatuses")
    long countByTenantAndMonth(
            @Param("tenantId") String tenantId,
            @Param("monthStart") Instant monthStart,
            @Param("monthEnd") Instant monthEnd,
            @Param("excludedStatuses") List<AppointmentStatus> excludedStatuses);

    @Query("SELECT a.status, COUNT(a) FROM AppointmentJpaEntity a WHERE a.tenantId = :tenantId AND a.startTime >= :monthStart AND a.startTime < :monthEnd GROUP BY a.status")
    List<Object[]> countByStatusGrouped(@Param("tenantId") String tenantId, @Param("monthStart") Instant monthStart, @Param("monthEnd") Instant monthEnd);

    @Query("SELECT a.source, COUNT(a) FROM AppointmentJpaEntity a WHERE a.tenantId = :tenantId AND a.startTime >= :monthStart AND a.startTime < :monthEnd GROUP BY a.source")
    List<Object[]> countBySourceGrouped(@Param("tenantId") String tenantId, @Param("monthStart") Instant monthStart, @Param("monthEnd") Instant monthEnd);

    List<AppointmentJpaEntity> findByClientIdAndTenantId(String clientId, String tenantId);

    /**
     * Paginated variant for the client appointment history (D38). {@code Pageable}
     * carries its own {@code Sort} (built by the caller as {@code startTime DESC}) —
     * this derived query honors it automatically.
     */
    Page<AppointmentJpaEntity> findByClientIdAndTenantId(String clientId, String tenantId, Pageable pageable);

    /**
     * Single aggregate row: {@code COUNT}, {@code SUM(servicePrice)} and
     * {@code MAX(startTime)} over the appointments of one client filtered by status.
     * With no matching rows, SQL still returns one row: count = 0, sum/max = NULL.
     *
     * <p>Returns a dedicated projection built via a JPQL {@code new} constructor
     * expression rather than {@code Object[]}. An {@code Object[]} return type is
     * collection-like to Spring Data ({@code TypeInformation.isCollectionLike()}
     * returns {@code true} for arrays), so it would be executed as a
     * {@code CollectionExecution} and the single aggregate row would come back nested
     * one level deeper than expected ({@code Object[]{ Object[]{count, sum, max} }}).
     */
    @Query("""
            SELECT new com.rivoo.appointment.infrastructure.adapter.out.persistence.repository.AppointmentAggregateProjection(
                COUNT(a), SUM(a.servicePrice), MAX(a.startTime))
            FROM AppointmentJpaEntity a
            WHERE a.clientId = :clientId AND a.tenantId = :tenantId AND a.status = :status
            """)
    AppointmentAggregateProjection aggregateByClientAndStatus(
            @Param("clientId") String clientId,
            @Param("tenantId") String tenantId,
            @Param("status") AppointmentStatus status);
}
