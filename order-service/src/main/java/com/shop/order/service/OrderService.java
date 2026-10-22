package com.shop.order.service;

import com.shop.order.client.ProductClient;
import com.shop.order.client.ProductInfo;
import com.shop.order.dto.CreateOrderRequest;
import com.shop.order.dto.OrderItemRequest;
import com.shop.order.dto.OrderResponse;
import com.shop.order.dto.PageResponse;
import com.shop.order.entity.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderTxService tx;
    private final ProductClient products;

    public OrderResponse create(Long userId, String idempotencyKey, CreateOrderRequest req) {
        if (idempotencyKey != null) {
            var existing = tx.findByKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        // Merge duplicate product lines so each product is reserved once.
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderItemRequest i : req.items()) {
            merged.merge(i.productId(), i.quantity(), Integer::sum);
        }

        List<Reserved> reserved = new ArrayList<>();
        try {
            for (var e : merged.entrySet()) {
                ProductInfo p = products.reserve(e.getKey(), e.getValue());
                reserved.add(new Reserved(p.id(), p.name(), p.price(), e.getValue()));
            }
            return tx.save(userId, idempotencyKey, reserved);
        } catch (DataIntegrityViolationException dup) {
            // Concurrent retry with the same Idempotency-Key won the race: undo ours, return theirs.
            releaseAll(reserved);
            if (idempotencyKey != null) {
                return tx.findByKey(userId, idempotencyKey).orElseThrow(() -> dup);
            }
            throw dup;
        } catch (RuntimeException ex) {
            releaseAll(reserved);
            throw ex;
        }
    }

    public OrderResponse cancel(Long id, Long userId, boolean admin) {
        List<Reserved> lines = tx.cancel(id, userId, admin);
        releaseAll(lines);
        return tx.get(id, userId, admin);
    }

    public OrderResponse get(Long id, Long userId, boolean admin) {
        return tx.get(id, userId, admin);
    }

    public PageResponse<OrderResponse> listMine(Long userId, Pageable pageable) {
        return tx.listMine(userId, pageable);
    }

    public PageResponse<OrderResponse> listAll(OrderStatus status, Pageable pageable) {
        return tx.listAll(status, pageable);
    }

    public OrderResponse updateStatus(Long id, OrderStatus next) {
        return tx.updateStatus(id, next);
    }

    private void releaseAll(List<Reserved> lines) {
        lines.forEach(l -> products.release(l.productId(), l.quantity()));
    }
}