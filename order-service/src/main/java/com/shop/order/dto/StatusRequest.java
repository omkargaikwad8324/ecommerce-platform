package com.shop.order.dto;

import com.shop.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull OrderStatus status) {
}