package com.rivoo.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory IP-based rate limiting with sliding window.
 * Two tiers:
 *   - Public booking (POST /api/v1/appointments/book): 10 req/min
 *   - General: 100 req/min
 */
@Slf4j
@Component
public class RateLimitingFilter implements GlobalFilter, Ordered {

    private static final int GENERAL_LIMIT = 100;
    private static final int BOOKING_LIMIT = 10;
    private static final long WINDOW_SECONDS = 60;
    private static final String BOOKING_PATH = "/api/v1/appointments/book";

    private final Map<String, Deque<Instant>> generalBuckets = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> bookingBuckets = new ConcurrentHashMap<>();

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = extractClientIp(exchange);
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        boolean isBooking = BOOKING_PATH.equals(path) && HttpMethod.POST.equals(method);

        Map<String, Deque<Instant>> buckets = isBooking ? bookingBuckets : generalBuckets;
        int limit = isBooking ? BOOKING_LIMIT : GENERAL_LIMIT;

        if (!isAllowed(clientIp, buckets, limit)) {
            log.atWarn()
                    .addKeyValue("clientIp", clientIp)
                    .addKeyValue("path", path)
                    .addKeyValue("limit", limit)
                    .log("Rate limit exceeded");

            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().set("Retry-After", "60");
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    private boolean isAllowed(String clientIp, Map<String, Deque<Instant>> buckets, int limit) {
        Deque<Instant> timestamps = buckets.computeIfAbsent(clientIp, k -> new ConcurrentLinkedDeque<>());
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(WINDOW_SECONDS);

        // Remove expired entries
        Iterator<Instant> it = timestamps.iterator();
        while (it.hasNext()) {
            if (it.next().isBefore(windowStart)) {
                it.remove();
            } else {
                break;
            }
        }

        if (timestamps.size() >= limit) {
            return false;
        }

        timestamps.addLast(now);
        return true;
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
