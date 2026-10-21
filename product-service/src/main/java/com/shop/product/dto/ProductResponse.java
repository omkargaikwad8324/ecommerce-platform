package com.shop.product.dto;

import java.math.BigDecimal;

public record ProductResponse(Long id, String name, String description, String category,
                              BigDecimal price, int stock, boolean active) {
}