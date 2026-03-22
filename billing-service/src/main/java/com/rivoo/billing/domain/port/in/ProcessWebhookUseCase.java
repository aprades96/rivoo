package com.rivoo.billing.domain.port.in;

public interface ProcessWebhookUseCase {

    void processEvent(String payload, String signatureHeader);
}
