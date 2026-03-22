package com.rivoo.salon.domain.port.out;

public interface NotificationServicePort {

    void sendWelcomeEmail(String tenantId, String recipientEmail, String salonName);
}
