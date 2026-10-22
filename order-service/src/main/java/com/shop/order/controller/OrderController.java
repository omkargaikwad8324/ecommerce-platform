package com.shop.order.controller;

import com.shop.order.dto.CreateOrderRequest;
import com.shop.order.dto.OrderResponse;
import com.shop.order.dto.PageResponse;
import com.shop.order.dto.StatusRequest;
import com.shop.order.entity.OrderStatus;
import com.shop.order.exception.ApiException;
import com.shop.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService service;

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request, Authentication auth) {
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > 100)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be 1-100 characters");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(userId(auth), idempotencyKey, request));
    }

    @GetMapping
    public PageResponse<OrderResponse> mine(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size, Authentication auth) {
        return service.listMine(userId(auth), pageable(page, size));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id, Authentication auth) {
        return service.get(id, userId(auth), isAdmin(auth));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id, Authentication auth) {
        return service.cancel(id, userId(auth), isAdmin(auth));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<OrderResponse> all(@RequestParam(required = false) OrderStatus status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return service.listAll(status, pageable(page, size));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    private Long userId(Authentication auth) {
        return Long.valueOf(auth.getName());
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by("id").descending());
    }
}