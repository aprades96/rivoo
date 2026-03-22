package com.rivoo.notification.application;

import com.rivoo.notification.application.dto.ScheduleNotificationRequest;
import com.rivoo.notification.application.dto.SendNotificationRequest;
import com.rivoo.notification.domain.model.Notification;
import com.rivoo.notification.domain.model.NotificationChannel;
import com.rivoo.notification.domain.model.NotificationStatus;
import com.rivoo.notification.domain.model.NotificationType;
import com.rivoo.notification.domain.port.out.MailSenderPort;
import com.rivoo.notification.domain.port.out.NotificationPersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationPersistencePort persistencePort;

    @Mock
    private MailSenderPort mailSenderPort;

    private NotificationTemplateEngine templateEngine;
    private NotificationService notificationService;

    private static final String TENANT_ID = "sal_tenant-001";
    private static final String RECIPIENT_EMAIL = "client@salon.com";

    @BeforeEach
    void setUp() {
        // NotificationTemplateEngine has no dependencies — use real instance
        templateEngine = new NotificationTemplateEngine();
        notificationService = new NotificationService(persistencePort, mailSenderPort, templateEngine);
    }

    // ── send: renders template, saves notification, calls mailSender ─────

    @Test
    void send_happyPath_rendersTemplateAndSendsEmail() throws Exception {
        SendNotificationRequest request = new SendNotificationRequest(
                TENANT_ID, RECIPIENT_EMAIL, "WELCOME",
                null, null, Map.of("salonName", "Barberia Norte"));

        when(persistencePort.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(1L);
            return n;
        });

        notificationService.send(request);

        // Email must be sent with rendered subject and body
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailSenderPort).send(anyString(), subjectCaptor.capture(), bodyCaptor.capture());

        assertThat(subjectCaptor.getValue()).isEqualTo("Bienvenido a Rivoo");
        assertThat(bodyCaptor.getValue()).contains("Barberia Norte");
    }

    @Test
    void send_happyPath_saveCalledTwice_finalStateIsSent() throws Exception {
        SendNotificationRequest request = new SendNotificationRequest(
                TENANT_ID, RECIPIENT_EMAIL, "WELCOME", null, null, Map.of());

        when(persistencePort.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.send(request);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(persistencePort, times(2)).save(captor.capture());

        // Both captures reference the same mutable object — verify final state
        Notification finalState = captor.getValue();
        assertThat(finalState.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(finalState.getSentAt()).isNotNull();
    }

    @Test
    void send_happyPath_notificationHasCorrectFields() throws Exception {
        SendNotificationRequest request = new SendNotificationRequest(
                TENANT_ID, RECIPIENT_EMAIL, "APPOINTMENT_CONFIRMATION",
                "APPOINTMENT", "apt_xyz789", Map.of("date", "2026-04-01", "time", "10:00", "employee", "Maria"));

        when(persistencePort.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.send(request);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(persistencePort, times(2)).save(captor.capture());
        Notification firstSave = captor.getAllValues().get(0);

        assertThat(firstSave.getExternalId()).startsWith("ntf_");
        assertThat(firstSave.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(firstSave.getRecipientEmail()).isEqualTo(RECIPIENT_EMAIL);
        assertThat(firstSave.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(firstSave.getType()).isEqualTo(NotificationType.APPOINTMENT_CONFIRMATION);
        assertThat(firstSave.getReferenceType()).isEqualTo("APPOINTMENT");
        assertThat(firstSave.getReferenceId()).isEqualTo("apt_xyz789");
        assertThat(firstSave.getRetryCount()).isZero();
    }

    @Test
    void send_mailSenderThrows_setsStatusFailed() throws Exception {
        SendNotificationRequest request = new SendNotificationRequest(
                TENANT_ID, RECIPIENT_EMAIL, "WELCOME", null, null, Map.of());

        when(persistencePort.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("SMTP error")).when(mailSenderPort).send(anyString(), anyString(), anyString());

        // Must not throw — failures are caught internally
        notificationService.send(request);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(persistencePort, times(2)).save(captor.capture());
        Notification lastSave = captor.getAllValues().get(1);
        assertThat(lastSave.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    // ── schedule: saves with scheduledFor, status=PENDING ───────────────

    @Test
    void schedule_savesNotificationWithPendingStatusAndScheduledFor() {
        Instant future = Instant.now().plusSeconds(3600);
        ScheduleNotificationRequest request = new ScheduleNotificationRequest(
                TENANT_ID, RECIPIENT_EMAIL, "APPOINTMENT_REMINDER",
                "APPOINTMENT", "apt_abc123", future,
                Map.of("date", "2026-04-01", "time", "11:00"));

        when(persistencePort.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.schedule(request);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(persistencePort).save(captor.capture());
        Notification saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getScheduledFor()).isEqualTo(future);
        assertThat(saved.getType()).isEqualTo(NotificationType.APPOINTMENT_REMINDER);
        assertThat(saved.getSentAt()).isNull();
    }

    @Test
    void schedule_doesNotCallMailSender() {
        Instant future = Instant.now().plusSeconds(3600);
        ScheduleNotificationRequest request = new ScheduleNotificationRequest(
                TENANT_ID, RECIPIENT_EMAIL, "APPOINTMENT_REMINDER",
                "APPOINTMENT", "apt_123", future, Map.of());

        when(persistencePort.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.schedule(request);

        verify(mailSenderPort, never()).send(anyString(), anyString(), anyString());
    }

    // ── cancelByAppointment: delegates to persistence ────────────────────

    @Test
    void cancelByAppointment_callsPersistenceCancelByReferenceId() {
        String appointmentId = "apt_cancel001";

        notificationService.cancelByAppointment(appointmentId);

        verify(persistencePort).cancelByReferenceId(appointmentId, "APPOINTMENT");
    }

    // ── Template rendering: each type produces correct subject/body ───────

    @Test
    void templateEngine_welcome_producesCorrectSubjectAndBody() {
        NotificationTemplateEngine.TemplateResult result = templateEngine.render(
                NotificationType.WELCOME, Map.of("salonName", "Cortes Gracia"));

        assertThat(result.subject()).isEqualTo("Bienvenido a Rivoo");
        assertThat(result.body()).contains("Cortes Gracia");
    }

    @Test
    void templateEngine_appointmentConfirmation_includesDateTimeAndEmployee() {
        NotificationTemplateEngine.TemplateResult result = templateEngine.render(
                NotificationType.APPOINTMENT_CONFIRMATION,
                Map.of("date", "2026-04-15", "time", "10:30", "employee", "Pedro"));

        assertThat(result.subject()).isEqualTo("Cita confirmada");
        assertThat(result.body()).contains("2026-04-15");
        assertThat(result.body()).contains("10:30");
        assertThat(result.body()).contains("Pedro");
    }

    @Test
    void templateEngine_appointmentReminder_includesDateAndTime() {
        NotificationTemplateEngine.TemplateResult result = templateEngine.render(
                NotificationType.APPOINTMENT_REMINDER,
                Map.of("date", "2026-04-20", "time", "09:00"));

        assertThat(result.subject()).isEqualTo("Recordatorio de cita");
        assertThat(result.body()).contains("2026-04-20");
        assertThat(result.body()).contains("09:00");
    }

    @Test
    void templateEngine_appointmentCancellation_includesDate() {
        NotificationTemplateEngine.TemplateResult result = templateEngine.render(
                NotificationType.APPOINTMENT_CANCELLATION,
                Map.of("date", "2026-05-01"));

        assertThat(result.subject()).isEqualTo("Cita cancelada");
        assertThat(result.body()).contains("2026-05-01");
    }

    @Test
    void templateEngine_paymentFailed_producesCorrectSubject() {
        NotificationTemplateEngine.TemplateResult result = templateEngine.render(
                NotificationType.PAYMENT_FAILED, Map.of());

        assertThat(result.subject()).isEqualTo("Problema con tu pago");
        assertThat(result.body()).contains("método de pago");
    }

    @Test
    void templateEngine_subscriptionCanceled_producesCorrectSubject() {
        NotificationTemplateEngine.TemplateResult result = templateEngine.render(
                NotificationType.SUBSCRIPTION_CANCELED, Map.of());

        assertThat(result.subject()).isEqualTo("Suscripción cancelada");
        assertThat(result.body()).contains("cancelada");
    }

    @Test
    void templateEngine_nullTemplateData_usesEmptyDefaultsWithoutException() {
        // Must not throw NullPointerException when data is null
        NotificationTemplateEngine.TemplateResult result = templateEngine.render(
                NotificationType.WELCOME, null);

        assertThat(result.subject()).isEqualTo("Bienvenido a Rivoo");
        assertThat(result.body()).isNotNull();
    }
}
