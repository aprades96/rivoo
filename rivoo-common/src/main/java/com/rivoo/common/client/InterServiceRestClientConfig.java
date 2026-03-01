package com.rivoo.common.client;

import com.rivoo.common.observability.CorrelationIdFilter;
import com.rivoo.common.observability.LoggingInterceptor;
import com.rivoo.common.security.RivooSecurityProperties;
import com.rivoo.common.security.SecurityAutoConfiguration;
import com.rivoo.common.tenant.TenantContext;
import com.rivoo.common.web.RivooHeaders;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@AutoConfiguration(after = SecurityAutoConfiguration.class)
public class InterServiceRestClientConfig {

    private final String internalServiceKey;
    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;

    public InterServiceRestClientConfig(
            RivooSecurityProperties securityProperties,
            @Value("${rivoo.client.connect-timeout-seconds:2}") int connectTimeoutSeconds,
            @Value("${rivoo.client.read-timeout-seconds:3}") int readTimeoutSeconds) {
        this.internalServiceKey = securityProperties.internalServiceKey();
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    @Bean
    public RestClient.Builder interServiceRestClientBuilder(LoggingInterceptor loggingInterceptor) {
        return RestClient.builder()
                .requestInterceptor(headerPropagationInterceptor())
                .requestInterceptor(loggingInterceptor)
                .defaultHeaders(headers -> {})
                .requestFactory(clientHttpRequestFactory());
    }

    private ClientHttpRequestInterceptor headerPropagationInterceptor() {
        return (request, body, execution) -> {
            // Propagate Correlation ID
            String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
            if (correlationId != null) {
                request.getHeaders().set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
            }

            // Propagate Tenant ID
            String tenantId = TenantContext.getCurrentTenantId();
            if (tenantId != null) {
                request.getHeaders().set(RivooHeaders.TENANT_ID, tenantId);
            }

            // Propagate Internal Service Key
            if (internalServiceKey != null && !internalServiceKey.isEmpty()) {
                request.getHeaders().set(RivooHeaders.INTERNAL_SERVICE_KEY, internalServiceKey);
            }

            return execution.execute(request, body);
        };
    }

    private org.springframework.http.client.ClientHttpRequestFactory clientHttpRequestFactory() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return factory;
    }
}
