package com.rivoo.common.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        long start = System.currentTimeMillis();
        log.debug("Inter-service call: {} {}", request.getMethod(), request.getURI());

        ClientHttpResponse response = execution.execute(request, body);

        long duration = System.currentTimeMillis() - start;
        log.debug("Inter-service response: {} {} → {} ({}ms)",
                request.getMethod(), request.getURI(), response.getStatusCode(), duration);

        return response;
    }
}
