package com.shop.order.service;

import com.shop.order.dto.OrderItemResponse;
import com.shop.order.dto.OrderResponse;
import com.shop.order.dto.PageResponse;
import com.shop.order.entity.Order;
import com.shop.order.entity.OrderItem;
import com.shop.order.entity.OrderStatus;
import com.shop.order.exception.ApiException;
import com.shop.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderTxService {

    private final OrderRepository repo;

    @Transactional(readOnly = true)
    public Optional<OrderResponse> findByKey(Long userId, String key) {
        return repo.findByUserIdAndIdempotencyKey(userId, key).map(this::toResponse);
    }

    @Transactional
    public OrderResponse save(Long userId, String key, List<Reserved> lines) {
        Order order = new Order();
        order.setUserId(userId);
        order.setIdempotencyKey(key);

        BigDecimal total = BigDecimal.ZERO;
        for (Reserved r : lines) {
            OrderItem item = new OrderItem();
            item.setProductId(r.productId());
            item.setProductName(r.name());
            item.setUnitPrice(r.price());
            item.setQuantity(r.quantity());
            order.addItem(item);
            total = total.add(r.price().multiply(BigDecimal.valueOf(r.quantity())));
        }
        order.setTotalAmount(total);
        return toResponse(repo.saveAndFlush(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id, Long userId, boolean admin) {
        return toResponse(load(id, userId, admin));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listMine(Long userId, Pageable pageable) {
        return toPage(repo.findByUserId(userId, pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listAll(OrderStatus status, Pageable pageable) {
        return toPage(status == null ? repo.findAll(pageable) : repo.findByStatus(status, pageable));
    }

    /** Marks CANCELLED and returns the lines whose stock must be released. */
    @Transactional
    public List<Reserved> cancel(Long id, Long userId, boolean admin) {
        Order o = load(id, userId, admin);
        if (!o.getStatus().isCancellable()) {
            throw new ApiException(HttpStatus.CONFLICT, "Order cannot be cancelled in status " + o.getStatus());
        }
        o.setStatus(OrderStatus.CANCELLED);
        return o.getItems().stream()
                .map(i -> new Reserved(i.getProductId(), i.getProductName(), i.getUnitPrice(), i.getQuantity()))
                .toList();
    }

    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus next) {
        if (next == OrderStatus.CANCELLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use the cancel endpoint to cancel an order");
        }
        Order o = load(id, null, true);
        if (!o.getStatus().canMoveTo(next)) {
            throw new ApiException(HttpStatus.CONFLICT, "Cannot move from " + o.getStatus() + " to " + next);
        }
        o.setStatus(next);
        return toResponse(o);
    }

    private Order load(Long id, Long userId, boolean admin) {
        return repo.findById(id)
                .filter(o -> admin || o.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private PageResponse<OrderResponse> toPage(Page<Order> page) {
        return new PageResponse<>(page.map(this::toResponse).getContent(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private OrderResponse toResponse(Order o) {
        List<OrderItemResponse> items = o.getItems().stream()
                .map(i -> new OrderItemResponse(i.getProductId(), i.getProductName(), i.getUnitPrice(), i.getQuantity()))
                .toList();
        return new OrderResponse(o.getId(), o.getUserId(), o.getStatus().name(),
                o.getTotalAmount(), o.getCreatedAt(), items);
    }
}