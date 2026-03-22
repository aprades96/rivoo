package com.rivoo.notification.application;

import com.rivoo.common.util.ExternalIdGenerator;
import com.rivoo.notification.application.dto.ScheduleNotificationRequest;
import com.rivoo.notification.application.dto.SendNotificationRequest;
import com.rivoo.notification.domain.model.Notification;
import com.rivoo.notification.domain.model.NotificationChannel;
import com.rivoo.notification.domain.model.NotificationStatus;
import com.rivoo.notification.domain.model.NotificationType;
import com.rivoo.notification.domain.port.in.CancelNotificationUseCase;
import com.rivoo.notification.domain.port.in.ScheduleNotificationUseCase;
import com.rivoo.notification.domain.port.in.SendNotificationUseCase;
import com.rivoo.notification.domain.port.out.MailSenderPort;
import com.rivoo.notification.domain.port.out.NotificationPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService implements SendNotificationUseCase, ScheduleNotificationUseCase, CancelNotificationUseCase {

    private static final String REFERENCE_TYPE_APPOINTMENT = "APPOINTMENT";

    private final NotificationPersistencePort persistencePort;
    private final MailSenderPort mailSenderPort;
    private final NotificationTemplateEngine templateEngine;

    @Override
    @Transactional
    public void send(SendNotificationRequest request) {
        NotificationType type = NotificationType.valueOf(request.type());
        NotificationTemplateEngine.TemplateResult template = templateEngine.render(type, request.templateData());

        Notification notification = Notification.builder()
                .externalId(ExternalIdGenerator.generate("ntf"))
                .tenantId(request.tenantId())
                .recipientEmail(request.recipientEmail())
                .channel(NotificationChannel.EMAIL)
                .type(type)
                .referenceType(request.referenceType())
                .referenceId(request.referenceId())
                .subject(template.subject())
                .body(template.body())
                .status(NotificationStatus.PENDING)
                .scheduledFor(Instant.now())
                .retryCount(0)
                .build();

        Notification saved = persistencePort.save(notification);

        try {
            mailSenderPort.send(saved.getRecipientEmail(), saved.getSubject(), saved.getBody());
            saved.setStatus(NotificationStatus.SENT);
            saved.setSentAt(Instant.now());
            persistencePort.save(saved);
            log.atInfo()
                    .addKeyValue("externalId", saved.getExternalId())
                    .addKeyValue("type", type)
                    .addKeyValue("recipientEmail", request.recipientEmail())
                    .log("Notification sent successfully");
        } catch (Exception ex) {
            saved.setStatus(NotificationStatus.FAILED);
            persistencePort.save(saved);
            log.atError()
                    .addKeyValue("externalId", saved.getExternalId())
                    .addKeyValue("type", type)
                    .addKeyValue("recipientEmail", request.recipientEmail())
                    .setCause(ex)
                    .log("Failed to send notification");
        }
    }

    @Override
    @Transactional
    public void schedule(ScheduleNotificationRequest request) {
        NotificationType type = NotificationType.valueOf(request.type());
        NotificationTemplateEngine.TemplateResult template = templateEngine.render(type, request.templateData());

        Notification notification = Notification.builder()
                .externalId(ExternalIdGenerator.generate("ntf"))
                .tenantId(request.tenantId())
                .recipientEmail(request.recipientEmail())
                .channel(NotificationChannel.EMAIL)
                .type(type)
                .referenceType(request.referenceType())
                .referenceId(request.referenceId())
                .subject(template.subject())
                .body(template.body())
                .status(NotificationStatus.PENDING)
                .scheduledFor(request.scheduledFor())
                .retryCount(0)
                .build();

        persistencePort.save(notification);

        log.atInfo()
                .addKeyValue("type", type)
                .addKeyValue("recipientEmail", request.recipientEmail())
                .addKeyValue("scheduledFor", request.scheduledFor())
                .log("Notification scheduled");
    }

    @Override
    @Transactional
    public void cancelByAppointment(String appointmentExternalId) {
        persistencePort.cancelByReferenceId(appointmentExternalId, REFERENCE_TYPE_APPOINTMENT);
        log.atInfo()
                .addKeyValue("appointmentExternalId", appointmentExternalId)
                .log("Notifications cancelled for appointment");
    }

    @Transactional
    public void processPendingNotifications() {
        List<Notification> pending = persistencePort.findPendingReadyToSend(Instant.now());

        if (pending.isEmpty()) {
            return;
        }

        log.atInfo()
                .addKeyValue("count", pending.size())
                .log("Processing pending notifications");

        for (Notification notification : pending) {
            try {
                mailSenderPort.send(notification.getRecipientEmail(), notification.getSubject(), notification.getBody());
                notification.setStatus(NotificationStatus.SENT);
                notification.setSentAt(Instant.now());
                persistencePort.save(notification);
                log.atInfo()
                        .addKeyValue("externalId", notification.getExternalId())
                        .addKeyValue("type", notification.getType())
                        .log("Scheduled notification sent successfully");
            } catch (Exception ex) {
                notification.setStatus(NotificationStatus.FAILED);
                notification.setRetryCount(notification.getRetryCount() + 1);
                persistencePort.save(notification);
                log.atError()
                        .addKeyValue("externalId", notification.getExternalId())
                        .addKeyValue("type", notification.getType())
                        .addKeyValue("retryCount", notification.getRetryCount())
                        .setCause(ex)
                        .log("Failed to send scheduled notification");
            }
        }
    }
}
