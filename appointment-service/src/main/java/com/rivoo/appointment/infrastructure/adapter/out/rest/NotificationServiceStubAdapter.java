package com.rivoo.appointment.infrastructure.adapter.out.rest;

import com.rivoo.appointment.domain.model.Appointment;
import com.rivoo.appointment.domain.port.out.NotificationServicePort;
import com.rivoo.appointment.infrastructure.adapter.out.rest.dto.ScheduleNotificationRestRequest;
import com.rivoo.appointment.infrastructure.adapter.out.rest.dto.SendNotificationRestRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Component
public class NotificationServiceStubAdapter implements NotificationServicePort {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);

    private final RestClient restClient;

    public NotificationServiceStubAdapter(RestClient.Builder interServiceRestClientBuilder,
                                          @Value("${rivoo.services.notification-service.url}") String notificationServiceUrl) {
        this.restClient = interServiceRestClientBuilder
                .baseUrl(notificationServiceUrl)
                .build();
    }

    @Override
    public void scheduleReminder(Appointment appointment) {
        try {
            String scheduledFor = appointment.getStartTime()
                    .minusSeconds(24 * 60 * 60)
                    .toString();

            ScheduleNotificationRestRequest request = new ScheduleNotificationRestRequest(
                    appointment.getTenantId(),
                    appointment.getClientEmail(),
                    "APPOINTMENT_REMINDER",
                    "APPOINTMENT",
                    appointment.getExternalId(),
                    scheduledFor,
                    buildAppointmentTemplateData(appointment)
            );

            restClient.post()
                    .uri("/api/internal/notifications/schedule")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            log.atInfo()
                    .addKeyValue("appointmentId", appointment.getExternalId())
                    .addKeyValue("scheduledFor", scheduledFor)
                    .log("Reminder scheduled in notification-service");

        } catch (Exception e) {
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("appointmentId", appointment.getExternalId())
                    .log("Failed to schedule reminder in notification-service, continuing");
        }
    }

    @Override
    public void cancelReminders(String appointmentExternalId) {
        try {
            restClient.delete()
                    .uri("/api/internal/notifications/appointment/{appointmentId}", appointmentExternalId)
                    .retrieve()
                    .toBodilessEntity();

            log.atInfo()
                    .addKeyValue("appointmentId", appointmentExternalId)
                    .log("Reminders cancelled in notification-service");

        } catch (Exception e) {
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("appointmentId", appointmentExternalId)
                    .log("Failed to cancel reminders in notification-service, continuing");
        }
    }

    @Override
    public void sendConfirmation(Appointment appointment) {
        try {
            SendNotificationRestRequest request = new SendNotificationRestRequest(
                    appointment.getTenantId(),
                    appointment.getClientEmail(),
                    "APPOINTMENT_CONFIRMATION",
                    "APPOINTMENT",
                    appointment.getExternalId(),
                    buildAppointmentTemplateData(appointment)
            );

            restClient.post()
                    .uri("/api/internal/notifications/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            log.atInfo()
                    .addKeyValue("appointmentId", appointment.getExternalId())
                    .addKeyValue("clientEmail", appointment.getClientEmail())
                    .log("Confirmation sent via notification-service");

        } catch (Exception e) {
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("appointmentId", appointment.getExternalId())
                    .log("Failed to send confirmation via notification-service, continuing");
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private Map<String, String> buildAppointmentTemplateData(Appointment appointment) {
        return Map.of(
                "date", DATE_FORMATTER.format(appointment.getStartTime()),
                "time", TIME_FORMATTER.format(appointment.getStartTime()),
                "employee", appointment.getEmployeeName(),
                "service", appointment.getServiceName()
        );
    }
}
