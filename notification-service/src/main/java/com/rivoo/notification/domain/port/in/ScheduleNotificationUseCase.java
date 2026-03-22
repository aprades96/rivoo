package com.rivoo.notification.domain.port.in;

import com.rivoo.notification.application.dto.ScheduleNotificationRequest;

public interface ScheduleNotificationUseCase {

    void schedule(ScheduleNotificationRequest request);
}
