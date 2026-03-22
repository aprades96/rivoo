package com.rivoo.notification.infrastructure.config;

import com.rivoo.notification.application.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class NotificationSchedulerCron {

    private final NotificationService notificationService;

    @Scheduled(fixedRate = 60000)
    public void processPendingNotifications() {
        log.atDebug().log("Cron: processing pending notifications");
        notificationService.processPendingNotifications();
    }
}
