package com.rivoo.common.tenant;

import com.rivoo.common.web.RivooHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

public class TenantInterceptor implements HandlerInterceptor {

    public static final String TENANT_HEADER = RivooHeaders.TENANT_ID;
    private static final String TENANT_MDC_KEY = "tenantId";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String tenantId = request.getHeader(TENANT_HEADER);
        if (tenantId != null && !tenantId.isBlank()) {
            TenantContext.setCurrentTenantId(tenantId);
            MDC.put(TENANT_MDC_KEY, tenantId);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        TenantContext.clear();
        MDC.remove(TENANT_MDC_KEY);
    }
}
