package com.shop.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver rateLimitKeyResolver() {
        return exchange -> {
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String routeId = route != null ? route.getId() : "unknown";

            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            String who;
            if (userId != null) {
                who = "user:" + userId;
            } else {
                var address = exchange.getRequest().getRemoteAddress();
                who = "ip:" + (address != null ? address.getAddress().getHostAddress() : "unknown");
            }
            return Mono.just(routeId + ":" + who);
        };
    }
}