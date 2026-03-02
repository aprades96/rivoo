package com.rivoo.client.infrastructure.adapter.in.web;

import com.rivoo.client.application.dto.ClientInternalResponse;
import com.rivoo.client.application.dto.FindOrCreateClientRequest;
import com.rivoo.client.domain.port.in.InternalClientUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/internal/clients")
@RequiredArgsConstructor
public class ClientInternalController {

    private final InternalClientUseCase internalClientUseCase;

    @GetMapping("/{clientId}")
    public ResponseEntity<ClientInternalResponse> getClient(
            @PathVariable String clientId,
            @RequestParam String tenantId) {
        log.atInfo().addKeyValue("clientId", clientId).log("GET /api/internal/clients");
        ClientInternalResponse response = internalClientUseCase.getByExternalIdAndTenant(clientId, tenantId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/find-or-create")
    public ResponseEntity<ClientInternalResponse> findOrCreate(
            @RequestParam String tenantId,
            @Valid @RequestBody FindOrCreateClientRequest request) {
        log.atInfo().log("POST /api/internal/clients/find-or-create");
        ClientInternalResponse response = internalClientUseCase.findOrCreate(tenantId, request);
        return ResponseEntity.ok(response);
    }
}
