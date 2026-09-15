package com.kaizenchandra.awseventbridgedemo.shared.adapter.in;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-replica admission bounds. An edge WAF supplies distributed per-client limits.
 */
@Component
@Order(-200)
public class RequestLimits implements WebFilter {
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger live = new AtomicInteger();
    private final AtomicLong window = new AtomicLong();
    private final AtomicInteger arrivals = new AtomicInteger();

    public static Mono<Void> reject(ServerWebExchange e, int status, String code) {
        e.getResponse().setStatusCode(HttpStatusCode.valueOf(status));
        e.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (status == 429 || status == 503) e.getResponse().getHeaders().set("Retry-After", "2");
        return e.getResponse().writeWith(Mono.just(e.getResponse().bufferFactory().wrap(("{\"status\":" + status + ",\"code\":\"" + code + "\",\"title\":\"" + code + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }

    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long now = System.currentTimeMillis() / 1000;
        long old = window.get();
        if (old != now && window.compareAndSet(old, now)) arrivals.set(0);
        boolean sse = exchange.getRequest().getPath().value().startsWith("/api/live/");
        if (exchange.getRequest().getHeaders().getContentLength() > 65536)
            return reject(exchange, 413, "REQUEST_TOO_LARGE");
        if (arrivals.incrementAndGet() > 200) return reject(exchange, 429, "RATE_LIMITED");
        AtomicInteger counter = sse ? live : active;
        int limit = sse ? 100 : 128;
        if (counter.incrementAndGet() > limit) {
            counter.decrementAndGet();
            return reject(exchange, 503, "OVERLOADED");
        }
        return chain.filter(exchange).doFinally(signal -> counter.decrementAndGet());
    }
}
