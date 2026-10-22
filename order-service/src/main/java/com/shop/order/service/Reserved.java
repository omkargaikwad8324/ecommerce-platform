package com.shop.order.service;

import java.math.BigDecimal;

public record Reserved(Long productId, String name, BigDecimal price, int quantity) {
}