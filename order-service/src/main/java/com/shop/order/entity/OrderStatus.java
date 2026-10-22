package com.shop.order.entity;

public enum OrderStatus {
    CREATED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED;

    public boolean canMoveTo(OrderStatus next) {
        return switch (this) {
            case CREATED -> next == CONFIRMED;
            case CONFIRMED -> next == SHIPPED;
            case SHIPPED -> next == DELIVERED;
            default -> false;
        };
    }

    public boolean isCancellable() {
        return this == CREATED || this == CONFIRMED;
    }
}