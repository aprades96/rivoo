package com.rivoo.notification.domain.port.out;

public interface MailSenderPort {

    void send(String to, String subject, String body);
}
