package com.rivoo.notification.domain.port.out;

import com.rivoo.notification.domain.model.Notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationPersistencePort {

    Notification save(Notification notification);

    List<Notification> findPendingReadyToSend(Instant now);

    void cancelByReferenceId(String referenceId, String referenceType);

    Optional<Notification> findByExternalId(String externalId);
}
