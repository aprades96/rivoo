package com.rivoo.common.client;

import com.rivoo.common.observability.CorrelationIdFilter;
import com.rivoo.common.observability.LoggingInterceptor;
import com.rivoo.common.tenant.TenantContext;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@AutoConfiguration
public class InterServiceRestClientConfig {

    @Value("${rivoo.security.internal-service-key:}")
    private String internalServiceKey;

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
                request.getHeaders().set("X-Tenant-Id", tenantId);
            }

            // Propagate Internal Service Key
            if (internalServiceKey != null && !internalServiceKey.isEmpty()) {
                request.getHeaders().set("X-Internal-Service-Key", internalServiceKey);
            }

            return execution.execute(request, body);
        };
    }

    private org.springframework.http.client.ClientHttpRequestFactory clientHttpRequestFactory() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return factory;
    }
}
