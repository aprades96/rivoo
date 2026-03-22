package com.rivoo.notification.domain.port.in;

import com.rivoo.notification.application.dto.SendNotificationRequest;

public interface SendNotificationUseCase {

    void send(SendNotificationRequest request);
}
