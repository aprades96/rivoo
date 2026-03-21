package com.rivoo.appointment.domain.port.out;

import com.rivoo.appointment.domain.model.Appointment;

public interface NotificationServicePort {

    void scheduleReminder(Appointment appointment);

    void cancelReminders(String appointmentExternalId);

    void sendConfirmation(Appointment appointment);
}
