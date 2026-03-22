package com.rivoo.notification.infrastructure.adapter.in.web;

import com.rivoo.notification.application.dto.ScheduleNotificationRequest;
import com.rivoo.notification.application.dto.SendNotificationRequest;
import com.rivoo.notification.domain.port.in.CancelNotificationUseCase;
import com.rivoo.notification.domain.port.in.ScheduleNotificationUseCase;
import com.rivoo.notification.domain.port.in.SendNotificationUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/internal/notifications")
@RequiredArgsConstructor
public class NotificationInternalController {

    private final SendNotificationUseCase sendNotificationUseCase;
    private final ScheduleNotificationUseCase scheduleNotificationUseCase;
    private final CancelNotificationUseCase cancelNotificationUseCase;

    @PostMapping("/send")
    public ResponseEntity<Void> send(@Valid @RequestBody SendNotificationRequest request) {
        log.atInfo()
                .addKeyValue("type", request.type())
                .addKeyValue("recipientEmail", request.recipientEmail())
                .log("POST /api/internal/notifications/send");
        sendNotificationUseCase.send(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/schedule")
    public ResponseEntity<Void> schedule(@Valid @RequestBody ScheduleNotificationRequest request) {
        log.atInfo()
                .addKeyValue("type", request.type())
                .addKeyValue("recipientEmail", request.recipientEmail())
                .addKeyValue("scheduledFor", request.scheduledFor())
                .log("POST /api/internal/notifications/schedule");
        scheduleNotificationUseCase.schedule(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/appointment/{appointmentId}")
    public ResponseEntity<Void> cancelByAppointment(@PathVariable String appointmentId) {
        log.atInfo()
                .addKeyValue("appointmentId", appointmentId)
                .log("DELETE /api/internal/notifications/appointment/{appointmentId}");
        cancelNotificationUseCase.cancelByAppointment(appointmentId);
        return ResponseEntity.ok().build();
    }
}
