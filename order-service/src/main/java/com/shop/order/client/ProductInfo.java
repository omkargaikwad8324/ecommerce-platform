package com.shop.order.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductInfo(Long id, String name, BigDecimal price, int stock) {
}