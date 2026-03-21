package com.rivoo.appointment.infrastructure.adapter.out.rest;

import com.rivoo.appointment.domain.model.Appointment;
import com.rivoo.appointment.domain.port.out.NotificationServicePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stub adapter until notification-service is implemented (Fase 8).
 * Logs the intent but does not send actual notifications.
 */
@Slf4j
@Component
public class NotificationServiceStubAdapter implements NotificationServicePort {

    @Override
    public void scheduleReminder(Appointment appointment) {
        log.atInfo()
                .addKeyValue("appointmentId", appointment.getExternalId())
                .addKeyValue("startTime", appointment.getStartTime())
                .log("Notification stub: would schedule reminder");
    }

    @Override
    public void cancelReminders(String appointmentExternalId) {
        log.atInfo()
                .addKeyValue("appointmentId", appointmentExternalId)
                .log("Notification stub: would cancel reminders");
    }

    @Override
    public void sendConfirmation(Appointment appointment) {
        log.atInfo()
                .addKeyValue("appointmentId", appointment.getExternalId())
                .addKeyValue("clientEmail", appointment.getClientEmail())
                .log("Notification stub: would send confirmation");
    }
}
