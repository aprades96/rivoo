package com.rivoo.common.exception;

import org.springframework.http.HttpStatus;

public class TenantMismatchException extends RivooException {

    public TenantMismatchException(String message) {
        super(message, "tenant-mismatch", "Tenant Mismatch", HttpStatus.FORBIDDEN);
    }

    public TenantMismatchException() {
        super("Tenant mismatch: the requested resource does not belong to the current tenant",
                "tenant-mismatch", "Tenant Mismatch", HttpStatus.FORBIDDEN);
    }
}
