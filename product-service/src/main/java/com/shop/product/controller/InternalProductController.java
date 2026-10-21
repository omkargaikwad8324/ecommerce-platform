package com.shop.product.controller;

import com.shop.product.dto.ProductResponse;
import com.shop.product.dto.StockRequest;
import com.shop.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/products")
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductService service;

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping("/{id}/reserve")
    public ProductResponse reserve(@PathVariable Long id, @Valid @RequestBody StockRequest request) {
        return service.reserve(id, request.quantity());
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<Void> release(@PathVariable Long id, @Valid @RequestBody StockRequest request) {
        service.release(id, request.quantity());
        return ResponseEntity.noContent().build();
    }
}