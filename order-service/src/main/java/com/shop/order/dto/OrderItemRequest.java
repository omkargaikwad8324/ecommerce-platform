package com.shop.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(@NotNull Long productId, @Min(1) @Max(100) int quantity) {
}