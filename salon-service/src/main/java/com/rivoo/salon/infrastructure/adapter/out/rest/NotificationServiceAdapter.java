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

    /**
     * The notification log has no tenant to attribute this mail to: it is sent precisely because we
     * refuse to resolve who owns the address. A literal marker keeps the row honest instead of
     * inventing a {@code sal_} id.
     */
    private static final String NO_TENANT = "PLATFORM";

    private static final String EXISTING_ACCOUNT_TYPE = "REGISTRATION_ATTEMPT_EXISTING_ACCOUNT";

    @Override
    public void sendExistingAccountRegistrationAttempt(String recipientEmail) {
        try {
            SendNotificationRestRequest request = new SendNotificationRestRequest(
                    NO_TENANT,
                    recipientEmail,
                    EXISTING_ACCOUNT_TYPE,
                    "REGISTRATION",
                    null,
                    Map.of()
            );

            restClient.post()
                    .uri("/api/internal/notifications/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            log.atInfo().log("Existing-account registration attempt notified via notification-service");

        } catch (Exception e) {
            // Swallowed for the same reason sendWelcomeEmail swallows: notification-service is
            // non-critical. Here it is also a SECURITY requirement, not just resilience — letting
            // this throw would turn "the address already exists" into a distinguishable HTTP
            // failure on an anonymous endpoint, which is the enumeration oracle this whole path
            // exists to close. Nothing about the address goes in the log line either.
            log.atWarn()
                    .setCause(e)
                    .log("Failed to notify existing-account registration attempt, continuing");
        }
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
