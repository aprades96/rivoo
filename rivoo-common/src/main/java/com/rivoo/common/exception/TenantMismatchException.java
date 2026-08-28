package com.rivoo.common.exception;

import org.springframework.http.HttpStatus;

/**
 * No {@code clientSafeDetail()} override, deliberately. It currently has no throw site anywhere in
 * the monorepo, so "reachable only from authenticated endpoints" cannot be established by
 * inspection; and it lives in rivoo-common, so the first service to throw it could just as well
 * be on an anonymous path. The {@code String} constructor also accepts an arbitrary message,
 * which a caller could fill with identifiers. A 403 whose {@code title} is already
 * "Tenant Mismatch" loses nothing by publishing the generic detail.
 */
public class TenantMismatchException extends RivooException {

    public TenantMismatchException(String message) {
        super(message, "tenant-mismatch", "Tenant Mismatch", HttpStatus.FORBIDDEN);
    }

    public TenantMismatchException() {
        super("Tenant mismatch: the requested resource does not belong to the current tenant",
                "tenant-mismatch", "Tenant Mismatch", HttpStatus.FORBIDDEN);
    }
}
