package com.rivoo.common.web;

public final class RivooHeaders {

    public static final String TENANT_ID = "X-Tenant-Id";
    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String INTERNAL_SERVICE_KEY = "X-Internal-Service-Key";
    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String SUBSCRIPTION_PLAN = "X-Subscription-Plan";
    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";

    private RivooHeaders() {
    }
}
