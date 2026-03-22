package com.rivoo.billing.infrastructure.adapter.in.web;

import com.rivoo.billing.domain.port.in.ProcessWebhookUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final ProcessWebhookUseCase processWebhookUseCase;

    @PostMapping("/stripe")
    public ResponseEntity<Void> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader) {
        log.atInfo().log("POST /api/webhooks/stripe");
        processWebhookUseCase.processEvent(payload, signatureHeader);
        return ResponseEntity.ok().build();
    }
}
