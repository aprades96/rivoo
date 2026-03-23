package com.rivoo.billing.infrastructure.adapter.in.web;

import com.rivoo.billing.application.dto.CreateSubscriptionRequest;
import com.rivoo.billing.application.dto.PlanLimitsResponse;
import com.rivoo.billing.application.dto.SubscriptionResponse;
import com.rivoo.billing.application.dto.UpdateSubscriptionStatusRequest;
import com.rivoo.billing.domain.port.in.CreateSubscriptionUseCase;
import com.rivoo.billing.domain.port.in.ManagePlanLimitsUseCase;
import com.rivoo.billing.domain.port.in.UpdateSubscriptionStatusUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/internal/billing")
@RequiredArgsConstructor
public class BillingInternalController {

    private final CreateSubscriptionUseCase createSubscriptionUseCase;
    private final ManagePlanLimitsUseCase managePlanLimitsUseCase;
    private final UpdateSubscriptionStatusUseCase updateSubscriptionStatusUseCase;

    @PostMapping("/subscriptions")
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request) {
        log.atInfo().log("POST /api/internal/billing/subscriptions");
        SubscriptionResponse response = createSubscriptionUseCase.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/tenants/{tenantId}/plan-limits")
    public ResponseEntity<PlanLimitsResponse> getPlanLimits(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "false") boolean forWriteOperation) {
        log.atInfo()
                .addKeyValue("forWriteOperation", forWriteOperation)
                .log("GET /api/internal/billing/tenants/plan-limits");
        PlanLimitsResponse response = managePlanLimitsUseCase.getPlanLimits(tenantId, forWriteOperation);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/subscriptions/{tenantId}/status")
    public ResponseEntity<SubscriptionResponse> updateStatus(
            @PathVariable String tenantId,
            @Valid @RequestBody UpdateSubscriptionStatusRequest request) {
        log.atInfo().addKeyValue("status", request.status())
                .log("PUT /api/internal/billing/subscriptions/status");
        SubscriptionResponse response = updateSubscriptionStatusUseCase.updateStatus(tenantId, request.status());
        return ResponseEntity.ok(response);
    }
}
