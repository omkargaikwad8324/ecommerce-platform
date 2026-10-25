package com.shop.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Request-Id";
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    private static final Logger log = LoggerFactory.getLogger(RequestIdGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String id = (incoming != null && VALID.matcher(incoming).matches())
                ? incoming : UUID.randomUUID().toString();

        long start = System.nanoTime();
        ServerHttpRequest request = exchange.getRequest().mutate().header(HEADER, id).build();
        exchange.getResponse().getHeaders().set(HEADER, id);

        return chain.filter(exchange.mutate().request(request).build())
                .doFinally(signal -> log.info("rid={} {} {} -> {} in {}ms", id,
                        request.getMethod(), request.getURI().getPath(),
                        exchange.getResponse().getStatusCode(),
                        (System.nanoTime() - start) / 1_000_000));
    }

    @Override
    public int getOrder() {
        return -2; // before the JWT filter (-1), so even rejected requests get an id
    }
}