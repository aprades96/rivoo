package com.rivoo.notification.infrastructure.adapter.out.persistence.repository;

import com.rivoo.notification.infrastructure.adapter.out.persistence.entity.NotificationLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationLogJpaRepository extends JpaRepository<NotificationLogJpaEntity, Long> {

    Optional<NotificationLogJpaEntity> findByExternalId(String externalId);

    @Query("SELECT n FROM NotificationLogJpaEntity n WHERE n.status = 'PENDING' AND n.scheduledFor <= :now ORDER BY n.scheduledFor ASC")
    List<NotificationLogJpaEntity> findPendingReadyToSend(@Param("now") Instant now);

    @Modifying
    @Query("UPDATE NotificationLogJpaEntity n SET n.status = 'CANCELLED' WHERE n.referenceId = :refId AND n.referenceType = :refType AND n.status = 'PENDING'")
    void cancelByReferenceId(@Param("refId") String refId, @Param("refType") String refType);
}
