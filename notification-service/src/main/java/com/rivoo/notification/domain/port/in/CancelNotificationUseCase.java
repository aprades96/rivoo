package com.rivoo.notification.domain.port.in;

public interface CancelNotificationUseCase {

    void cancelByAppointment(String appointmentExternalId);
}
