package com.rivoo.common.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

@Slf4j
public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        long start = System.currentTimeMillis();
        log.atDebug().addKeyValue("method", request.getMethod()).addKeyValue("uri", request.getURI()).log("Inter-service call");

        ClientHttpResponse response = execution.execute(request, body);

        long duration = System.currentTimeMillis() - start;
        log.atDebug().addKeyValue("method", request.getMethod()).addKeyValue("uri", request.getURI()).addKeyValue("status", response.getStatusCode()).addKeyValue("durationMs", duration).log("Inter-service response");

        return response;
    }
}
