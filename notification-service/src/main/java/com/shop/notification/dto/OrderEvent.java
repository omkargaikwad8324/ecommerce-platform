package com.shop.notification.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderEvent(String eventId, String type, Long orderId, Long userId, String email,
                         String status, BigDecimal totalAmount, Instant occurredAt) {
}