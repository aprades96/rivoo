package com.rivoo.client.domain.exception;

import com.rivoo.common.exception.RivooException;
import org.springframework.http.HttpStatus;

public class DuplicateClientEmailException extends RivooException {

    public DuplicateClientEmailException(String email) {
        super("A client with email '" + email + "' already exists in this salon",
                "duplicate-client-email", "Duplicate Client Email", HttpStatus.CONFLICT);
    }

    /**
     * Authenticated-only by construction: client-service has no anonymous surface (it inherits
     * rivoo-common's SecurityConfig, anyRequest().authenticated()). The single throw site is
     * ClientService#create, behind hasRole('SALON_OWNER'), so the email echoed back is one the
     * caller just submitted for their own tenant - not an enumeration oracle.
     */
    @Override
    public String clientSafeDetail() {
        return getMessage();
    }
}
