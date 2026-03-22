package com.rivoo.notification.infrastructure.adapter.out.mail;

import com.rivoo.notification.domain.port.out.MailSenderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MailStubAdapter implements MailSenderPort {

    @Override
    public void send(String to, String subject, String body) {
        log.atInfo()
                .addKeyValue("to", to)
                .addKeyValue("subject", subject)
                .log("Mail stub: would send email");
    }
}
