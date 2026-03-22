package com.rivoo.billing.infrastructure.adapter.in.web;

import com.rivoo.billing.application.dto.CheckoutRequest;
import com.rivoo.billing.application.dto.CheckoutResponse;
import com.rivoo.billing.application.dto.PlanResponse;
import com.rivoo.billing.application.dto.SubscriptionResponse;
import com.rivoo.billing.domain.port.in.CheckoutUseCase;
import com.rivoo.billing.domain.port.in.GetSubscriptionUseCase;
import com.rivoo.billing.domain.port.in.ListPlansUseCase;
import com.rivoo.common.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final GetSubscriptionUseCase getSubscriptionUseCase;
    private final CheckoutUseCase checkoutUseCase;
    private final ListPlansUseCase listPlansUseCase;

    @GetMapping("/subscription")
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<SubscriptionResponse> getSubscription() {
        String tenantId = TenantContext.getCurrentTenantId();
        log.atInfo().log("GET /api/v1/billing/subscription");
        SubscriptionResponse response = getSubscriptionUseCase.getByTenantId(tenantId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/checkout-session")
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<CheckoutResponse> createCheckout(@Valid @RequestBody CheckoutRequest request) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.atInfo().addKeyValue("planName", request.planName()).log("POST /api/v1/billing/checkout-session");
        CheckoutResponse response = checkoutUseCase.createCheckoutSession(tenantId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/plans")
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<List<PlanResponse>> listPlans() {
        log.atInfo().log("GET /api/v1/billing/plans");
        List<PlanResponse> plans = listPlansUseCase.listActivePlans();
        return ResponseEntity.ok(plans);
    }
}
