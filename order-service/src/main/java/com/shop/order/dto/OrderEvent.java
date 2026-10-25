package com.shop.order.dto;

import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderEvent(String eventId, String type, Long orderId, Long userId, String email,
                         String status, BigDecimal totalAmount, Instant occurredAt, String requestId) {

    public static OrderEvent of(String type, OrderResponse o) {
        return new OrderEvent(UUID.randomUUID().toString(), type, o.id(), o.userId(), o.userEmail(),
                o.status(), o.totalAmount(), Instant.now(), MDC.get("rid"));
    }
}