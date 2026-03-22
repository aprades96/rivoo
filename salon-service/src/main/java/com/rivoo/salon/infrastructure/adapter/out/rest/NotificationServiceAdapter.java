package com.rivoo.salon.infrastructure.adapter.out.rest;

import com.rivoo.salon.domain.port.out.NotificationServicePort;
import com.rivoo.salon.infrastructure.adapter.out.rest.dto.SendNotificationRestRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class NotificationServiceAdapter implements NotificationServicePort {

    private final RestClient restClient;

    public NotificationServiceAdapter(RestClient.Builder interServiceRestClientBuilder,
                                      @Value("${rivoo.services.notification-service.url}") String notificationServiceUrl) {
        this.restClient = interServiceRestClientBuilder
                .baseUrl(notificationServiceUrl)
                .build();
    }

    @Override
    public void sendWelcomeEmail(String tenantId, String recipientEmail, String salonName) {
        try {
            SendNotificationRestRequest request = new SendNotificationRestRequest(
                    tenantId,
                    recipientEmail,
                    "WELCOME",
                    "SALON",
                    tenantId,
                    Map.of("salonName", salonName)
            );

            restClient.post()
                    .uri("/api/internal/notifications/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            log.atInfo()
                    .addKeyValue("salonName", salonName)
                    .log("Welcome email sent via notification-service");

        } catch (Exception e) {
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("salonName", salonName)
                    .log("Failed to send welcome email via notification-service, continuing");
        }
    }
}
