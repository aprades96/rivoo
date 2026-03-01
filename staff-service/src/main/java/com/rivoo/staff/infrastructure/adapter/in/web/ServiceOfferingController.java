package com.rivoo.staff.infrastructure.adapter.in.web;

import com.rivoo.common.tenant.TenantContext;
import com.rivoo.staff.application.dto.CreateServiceOfferingRequest;
import com.rivoo.staff.application.dto.ServiceOfferingResponse;
import com.rivoo.staff.application.dto.UpdateServiceOfferingRequest;
import com.rivoo.staff.domain.port.in.ManageServiceOfferingUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceOfferingController {

    private final ManageServiceOfferingUseCase manageServiceOfferingUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('SALON_OWNER', 'EMPLOYEE')")
    public ResponseEntity<Page<ServiceOfferingResponse>> list(Pageable pageable) {
        log.info("GET /api/v1/services");
        Page<ServiceOfferingResponse> response = manageServiceOfferingUseCase.list(pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<ServiceOfferingResponse> create(@Valid @RequestBody CreateServiceOfferingRequest request) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("POST /api/v1/services - tenantId={}", tenantId);
        ServiceOfferingResponse response = manageServiceOfferingUseCase.create(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<ServiceOfferingResponse> update(@PathVariable String id,
                                                           @Valid @RequestBody UpdateServiceOfferingRequest request) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("PUT /api/v1/services/{} - tenantId={}", id, tenantId);
        ServiceOfferingResponse response = manageServiceOfferingUseCase.update(tenantId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<Void> deactivate(@PathVariable String id) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("DELETE /api/v1/services/{} - tenantId={}", id, tenantId);
        manageServiceOfferingUseCase.deactivate(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
