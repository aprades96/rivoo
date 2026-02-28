package com.rivoo.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class InternalEndpointFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalEndpointFilter.class);
    private static final String INTERNAL_PATH_PREFIX = "/api/internal/";
    private static final String SERVICE_KEY_HEADER = "X-Internal-Service-Key";

    private final String expectedServiceKey;

    public InternalEndpointFilter(String expectedServiceKey) {
        this.expectedServiceKey = expectedServiceKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith(INTERNAL_PATH_PREFIX)) {
            String providedKey = request.getHeader(SERVICE_KEY_HEADER);
            if (expectedServiceKey == null || expectedServiceKey.isEmpty() || !expectedServiceKey.equals(providedKey)) {
                log.warn("Unauthorized internal endpoint access attempt: {} {}", request.getMethod(), path);
                response.setStatus(HttpStatus.FORBIDDEN.value());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
