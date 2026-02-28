package com.rivoo.common.exception;

public class TenantMismatchException extends RuntimeException {

    public TenantMismatchException(String message) {
        super(message);
    }

    public TenantMismatchException() {
        super("Tenant mismatch: the requested resource does not belong to the current tenant");
    }
}
