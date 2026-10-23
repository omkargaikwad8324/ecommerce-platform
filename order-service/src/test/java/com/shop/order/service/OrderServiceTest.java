package com.shop.order.service;

import com.shop.order.client.ProductClient;
import com.shop.order.client.ProductInfo;
import com.shop.order.dto.CreateOrderRequest;
import com.shop.order.dto.OrderItemRequest;
import com.shop.order.dto.OrderResponse;
import com.shop.order.exception.ApiException;
import com.shop.order.messaging.OrderEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderTxService tx;
    @Mock ProductClient products;
    @Mock OrderEventPublisher events;
    @InjectMocks OrderService service;

    private static final ProductInfo IPHONE = new ProductInfo(1L, "iPhone 15", new BigDecimal("100.00"), 8);
    private static final ProductInfo SONY = new ProductInfo(2L, "Sony", new BigDecimal("50.00"), 5);

    private OrderResponse response(String status) {
        return new OrderResponse(10L, 2L, "ravi@test.com", status, new BigDecimal("200.00"),
                Instant.now(), List.of());
    }

    private CreateOrderRequest req(OrderItemRequest... items) {
        return new CreateOrderRequest(List.of(items));
    }

    @Test
    void create_reservesEachProduct_savesAndPublishes() {
        when(products.reserve(1L, 2)).thenReturn(IPHONE);
        when(tx.save(eq(2L), eq("ravi@test.com"), isNull(), anyList())).thenReturn(response("CREATED"));

        OrderResponse r = service.create(2L, "ravi@test.com", null, req(new OrderItemRequest(1L, 2)));

        assertThat(r.id()).isEqualTo(10L);
        verify(events).publish(eq("order.created"), any());
        verify(products, never()).release(anyLong(), anyInt());
    }

    @Test
    void create_secondProductOutOfStock_releasesFirst() {
        when(products.reserve(1L, 1)).thenReturn(IPHONE);
        when(products.reserve(2L, 1)).thenThrow(new ApiException(HttpStatus.CONFLICT, "Insufficient stock for product 2"));

        assertThatThrownBy(() -> service.create(2L, "ravi@test.com", null,
                req(new OrderItemRequest(1L, 1), new OrderItemRequest(2L, 1))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Insufficient stock");

        verify(products).release(1L, 1);
        verify(products, never()).release(eq(2L), anyInt());
        verify(tx, never()).save(any(), any(), any(), any());
        verifyNoInteractions(events);
    }

    @Test
    void create_existingIdempotencyKey_returnsExistingWithoutReserving() {
        when(tx.findByKey(2L, "k1")).thenReturn(Optional.of(response("CREATED")));

        OrderResponse r = service.create(2L, "ravi@test.com", "k1", req(new OrderItemRequest(1L, 1)));

        assertThat(r.id()).isEqualTo(10L);
        verifyNoInteractions(products, events);
    }

    @Test
    void create_saveFails_releasesAllReserved() {
        when(products.reserve(1L, 1)).thenReturn(IPHONE);
        when(tx.save(any(), any(), any(), anyList())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.create(2L, "ravi@test.com", null, req(new OrderItemRequest(1L, 1))))
                .hasMessage("db down");

        verify(products).release(1L, 1);
        verifyNoInteractions(events);
    }

    @Test
    void create_mergesDuplicateProductLines() {
        when(products.reserve(1L, 3)).thenReturn(IPHONE);
        when(tx.save(any(), any(), any(), anyList())).thenReturn(response("CREATED"));

        service.create(2L, "ravi@test.com", null,
                req(new OrderItemRequest(1L, 1), new OrderItemRequest(1L, 2)));

        verify(products, times(1)).reserve(anyLong(), anyInt());
        verify(products).reserve(1L, 3);
    }

    @Test
    void create_duplicateKeyRace_returnsWinnerAndReleasesOwnStock() {
        when(products.reserve(1L, 1)).thenReturn(IPHONE);
        when(tx.findByKey(2L, "k1")).thenReturn(Optional.empty()).thenReturn(Optional.of(response("CREATED")));
        when(tx.save(any(), any(), eq("k1"), anyList())).thenThrow(new DataIntegrityViolationException("dup"));

        OrderResponse r = service.create(2L, "ravi@test.com", "k1", req(new OrderItemRequest(1L, 1)));

        assertThat(r.id()).isEqualTo(10L);
        verify(products).release(1L, 1);
        verifyNoInteractions(events);
    }

    @Test
    void cancel_releasesStockAndPublishes() {
        when(tx.cancel(10L, 2L, false)).thenReturn(List.of(new Reserved(2L, "Sony", SONY.price(), 3)));
        when(tx.get(10L, 2L, false)).thenReturn(response("CANCELLED"));

        OrderResponse r = service.cancel(10L, 2L, false);

        assertThat(r.status()).isEqualTo("CANCELLED");
        verify(products).release(2L, 3);
        verify(events).publish(eq("order.cancelled"), any());
    }
}