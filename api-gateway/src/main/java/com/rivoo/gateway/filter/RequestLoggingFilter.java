package com.rivoo.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Logs every gateway request with method, path, status, latency, IP, and correlation ID.
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        String clientIp = extractClientIp(exchange);
        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");

        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    long latency = System.currentTimeMillis() - startTime;
                    int status = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value() : 0;

                    log.atInfo()
                            .addKeyValue("method", method)
                            .addKeyValue("path", path)
                            .addKeyValue("status", status)
                            .addKeyValue("latencyMs", latency)
                            .addKeyValue("clientIp", clientIp)
                            .addKeyValue("correlationId", correlationId)
                            .log("Gateway request");
                }));
    }

    private String extractClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }
}
