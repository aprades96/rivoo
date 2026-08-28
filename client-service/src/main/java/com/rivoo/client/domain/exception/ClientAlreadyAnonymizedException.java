package com.rivoo.client.domain.exception;

import com.rivoo.common.exception.BusinessValidationException;

public class ClientAlreadyAnonymizedException extends BusinessValidationException {

    public ClientAlreadyAnonymizedException(String identifier) {
        super("Client '" + identifier + "' has already been anonymized");
    }

    /**
     * Authenticated-only by construction: client-service has no anonymous surface (it inherits
     * rivoo-common's SecurityConfig, anyRequest().authenticated()). Both throw sites are in the
     * GDPR flows behind hasRole('SALON_OWNER').
     */
    @Override
    public String clientSafeDetail() {
        return getMessage();
    }
}
