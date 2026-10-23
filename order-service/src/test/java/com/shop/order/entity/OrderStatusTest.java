package com.shop.order.entity;

import org.junit.jupiter.api.Test;

import static com.shop.order.entity.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void validTransitions_followTheLifecycle() {
        assertThat(CREATED.canMoveTo(CONFIRMED)).isTrue();
        assertThat(CONFIRMED.canMoveTo(SHIPPED)).isTrue();
        assertThat(SHIPPED.canMoveTo(DELIVERED)).isTrue();
    }

    @Test
    void invalidTransitions_areRejected() {
        assertThat(CREATED.canMoveTo(SHIPPED)).isFalse();
        assertThat(CONFIRMED.canMoveTo(DELIVERED)).isFalse();
        assertThat(DELIVERED.canMoveTo(CREATED)).isFalse();
        assertThat(CANCELLED.canMoveTo(CONFIRMED)).isFalse();
    }

    @Test
    void onlyCreatedAndConfirmedCanBeCancelled() {
        assertThat(CREATED.isCancellable()).isTrue();
        assertThat(CONFIRMED.isCancellable()).isTrue();
        assertThat(SHIPPED.isCancellable()).isFalse();
        assertThat(DELIVERED.isCancellable()).isFalse();
        assertThat(CANCELLED.isCancellable()).isFalse();
    }
}