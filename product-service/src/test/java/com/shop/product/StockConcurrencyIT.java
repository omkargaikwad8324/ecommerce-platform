package com.shop.product;

import com.shop.product.entity.Product;
import com.shop.product.exception.ApiException;
import com.shop.product.repository.ProductRepository;
import com.shop.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class StockConcurrencyIT {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired ProductService service;
    @Autowired ProductRepository repo;

    private Long newProduct(int stock) {
        Product p = new Product();
        p.setName("Test item");
        p.setCategory("test");
        p.setPrice(new BigDecimal("10.00"));
        p.setStock(stock);
        return repo.save(p).getId();
    }

    private int stockOf(Long id) {
        return repo.findById(id).orElseThrow().getStock();
    }

    @Test
    void concurrentReserve_neverOversells() throws Exception {
        Long id = newProduct(10);
        int threads = 30;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    service.reserve(id, 1);
                    ok.incrementAndGet();
                } catch (ApiException e) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<Void> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(ok.get()).isEqualTo(10);
        assertThat(rejected.get()).isEqualTo(20);
        assertThat(stockOf(id)).isZero();
    }

    @Test
    void reserveThenRelease_restoresStock() {
        Long id = newProduct(5);

        service.reserve(id, 2);
        assertThat(stockOf(id)).isEqualTo(3);

        service.release(id, 2);
        assertThat(stockOf(id)).isEqualTo(5);
    }

    @Test
    void reserve_moreThanStock_isRejectedAndStockUnchanged() {
        Long id = newProduct(1);

        assertThatThrownBy(() -> service.reserve(id, 2))
                .isInstanceOf(ApiException.class)
                .hasMessage("Insufficient stock");
        assertThat(stockOf(id)).isEqualTo(1);
    }
}