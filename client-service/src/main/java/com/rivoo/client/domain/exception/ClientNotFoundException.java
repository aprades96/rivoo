package com.rivoo.client.domain.exception;

import com.rivoo.common.exception.ResourceNotFoundException;

public class ClientNotFoundException extends ResourceNotFoundException {

    public ClientNotFoundException(String identifier) {
        super("client", identifier);
    }

    /**
     * Authenticated-only by construction: client-service declares no security config of its own,
     * so it inherits rivoo-common's SecurityConfig (anyRequest().authenticated()) and has no
     * anonymous surface at all. Every throw site is in ClientService, behind
     * hasAnyRole('SALON_OWNER','EMPLOYEE') or hasRole('SALON_OWNER').
     */
    @Override
    public String clientSafeDetail() {
        return getMessage();
    }
}
