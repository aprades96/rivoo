package com.rivoo.client.infrastructure.adapter.in.web;

import com.rivoo.client.application.dto.ClientExportResponse;
import com.rivoo.client.application.dto.ClientResponse;
import com.rivoo.client.application.dto.CreateClientRequest;
import com.rivoo.client.application.dto.UpdateClientRequest;
import com.rivoo.client.domain.port.in.AnonymizeClientUseCase;
import com.rivoo.client.domain.port.in.CreateClientUseCase;
import com.rivoo.client.domain.port.in.ExportClientDataUseCase;
import com.rivoo.client.domain.port.in.GetClientUseCase;
import com.rivoo.client.domain.port.in.UpdateClientUseCase;
import com.rivoo.common.tenant.TenantContext;
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
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientController {

    private final CreateClientUseCase createClientUseCase;
    private final GetClientUseCase getClientUseCase;
    private final UpdateClientUseCase updateClientUseCase;
    private final AnonymizeClientUseCase anonymizeClientUseCase;
    private final ExportClientDataUseCase exportClientDataUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('SALON_OWNER', 'EMPLOYEE')")
    public ResponseEntity<Page<ClientResponse>> list(Pageable pageable) {
        log.info("GET /api/v1/clients");
        Page<ClientResponse> response = getClientUseCase.list(pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody CreateClientRequest request) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("POST /api/v1/clients - tenantId={}", tenantId);
        ClientResponse response = createClientUseCase.create(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SALON_OWNER', 'EMPLOYEE')")
    public ResponseEntity<ClientResponse> getById(@PathVariable String id) {
        log.info("GET /api/v1/clients/{}", id);
        ClientResponse response = getClientUseCase.getByExternalId(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<ClientResponse> update(@PathVariable String id,
                                                  @Valid @RequestBody UpdateClientRequest request) {
        log.info("PUT /api/v1/clients/{}", id);
        ClientResponse response = updateClientUseCase.update(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<Void> anonymize(@PathVariable String id) {
        log.info("DELETE /api/v1/clients/{} (GDPR anonymize)", id);
        anonymizeClientUseCase.anonymize(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/export")
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<ClientExportResponse> export(@PathVariable String id) {
        log.info("GET /api/v1/clients/{}/export", id);
        ClientExportResponse response = exportClientDataUseCase.export(id);
        return ResponseEntity.ok(response);
    }
}
