package com.shop.product.service;

import com.shop.product.dto.PageResponse;
import com.shop.product.dto.ProductRequest;
import com.shop.product.dto.ProductResponse;
import com.shop.product.entity.Product;
import com.shop.product.exception.ApiException;
import com.shop.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repo;

    @Transactional
    public ProductResponse create(ProductRequest r) {
        Product p = new Product();
        apply(p, r);
        return toResponse(repo.save(p));
    }

    // Cache-aside: check Redis first, on a miss read the database and store the result.
    @Cacheable(cacheNames = "product", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        log.info("Cache MISS: loading product {} from database", id);
        return repo.findById(id)
                .filter(Product::isActive)
                .map(this::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String q, String category, Pageable pageable) {
        boolean hasQ = q != null && !q.isBlank();
        boolean hasCategory = category != null && !category.isBlank();

        Page<Product> page;
        if (hasQ && hasCategory) {
            page = repo.findByActiveTrueAndCategoryIgnoreCaseAndNameContainingIgnoreCase(
                    category.trim(), q.trim(), pageable);
        } else if (hasQ) {
            page = repo.findByActiveTrueAndNameContainingIgnoreCase(q.trim(), pageable);
        } else if (hasCategory) {
            page = repo.findByActiveTrueAndCategoryIgnoreCase(category.trim(), pageable);
        } else {
            page = repo.findByActiveTrue(pageable);
        }
        return new PageResponse<>(page.map(this::toResponse).getContent(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @CachePut(cacheNames = "product", key = "#id")
    @Transactional
    public ProductResponse update(Long id, ProductRequest r) {
        Product p = repo.findById(id).filter(Product::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
        apply(p, r);
        return toResponse(repo.saveAndFlush(p));
    }

    @CacheEvict(cacheNames = "product", key = "#id")
    @Transactional
    public void delete(Long id) {
        Product p = repo.findById(id).filter(Product::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
        p.setActive(false);
    }

    // Used by order-service. The cached copy is removed because the stock just changed.
    @CacheEvict(cacheNames = "product", key = "#id")
    @Transactional
    public ProductResponse reserve(Long id, int quantity) {
        int updated = repo.decrementStock(id, quantity);
        if (updated == 0) {
            boolean exists = repo.findById(id).filter(Product::isActive).isPresent();
            if (!exists) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Product not found");
            }
            throw new ApiException(HttpStatus.CONFLICT, "Insufficient stock");
        }
        log.info("Reserved {} x product {}", quantity, id);
        return toResponse(repo.findById(id).orElseThrow());
    }

    @CacheEvict(cacheNames = "product", key = "#id")
    @Transactional
    public void release(Long id, int quantity) {
        if (repo.incrementStock(id, quantity) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Product not found");
        }
    }

    private void apply(Product p, ProductRequest r) {
        p.setName(r.name().trim());
        p.setDescription(r.description());
        p.setCategory(r.category().trim());
        p.setPrice(r.price());
        p.setStock(r.stock());
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getCategory(),
                p.getPrice(), p.getStock(), p.isActive());
    }
}