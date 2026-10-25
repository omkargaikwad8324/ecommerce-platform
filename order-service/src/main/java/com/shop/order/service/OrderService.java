package com.shop.order.service;

import com.shop.order.client.ProductClient;
import com.shop.order.client.ProductInfo;
import com.shop.order.dto.*;
import com.shop.order.entity.OrderStatus;
import com.shop.order.messaging.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;


import org.springframework.data.domain.Pageable;
import com.shop.order.entity.OrderStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderTxService tx;
    private final ProductClient products;
    private final OrderEventPublisher events;   // add next to tx and products

    public OrderResponse create(Long userId, String email, String idempotencyKey, CreateOrderRequest req) {
        if (idempotencyKey != null) {
            var existing = tx.findByKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();   // replay: no new event
            }
        }

        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderItemRequest i : req.items()) {
            merged.merge(i.productId(), i.quantity(), Integer::sum);
        }

        List<Reserved> reserved = new ArrayList<>();
        OrderResponse saved;
        try {
            for (var e : merged.entrySet()) {
                ProductInfo p = products.reserve(e.getKey(), e.getValue());
                reserved.add(new Reserved(p.id(), p.name(), p.price(), e.getValue()));
            }
            saved = tx.save(userId, email, idempotencyKey, reserved);
        } catch (DataIntegrityViolationException dup) {
            releaseAll(reserved);
            if (idempotencyKey != null) {
                return tx.findByKey(userId, idempotencyKey).orElseThrow(() -> dup);
            }
            throw dup;
        } catch (RuntimeException ex) {
            releaseAll(reserved);
            throw ex;
        }
        // in create(), just before events.publish(...)
        log.info("Order {} created for user {}, total {}", saved.id(), userId, saved.totalAmount());

        events.publish("order.created", OrderEvent.of("ORDER_CREATED", saved));
        return saved;
    }

    public OrderResponse cancel(Long id, Long userId, boolean admin) {
        List<Reserved> lines = tx.cancel(id, userId, admin);
        releaseAll(lines);
        OrderResponse resp = tx.get(id, userId, admin);
        // in cancel(), just before events.publish(...)
        log.info("Order {} cancelled", id);
        events.publish("order.cancelled", OrderEvent.of("ORDER_CANCELLED", resp));
        return resp;
    }

    public OrderResponse updateStatus(Long id, OrderStatus next) {
        OrderResponse resp = tx.updateStatus(id, next);
        events.publish("order.status", OrderEvent.of("ORDER_STATUS_CHANGED", resp));
        return resp;
    }

    public OrderResponse get(Long id, Long userId, boolean admin) {
        return tx.get(id, userId, admin);
    }

    public PageResponse<OrderResponse> listMine(
            Long userId,
            Pageable pageable) {
        return tx.listMine(userId, pageable);
    }

    public PageResponse<OrderResponse> listAll(
            OrderStatus status,
            Pageable pageable) {
        return tx.listAll(status, pageable);
    }

    private void releaseAll(List<Reserved> lines) {
        lines.forEach(l -> products.release(l.productId(), l.quantity()));
    }
}