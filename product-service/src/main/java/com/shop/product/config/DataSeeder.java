package com.shop.product.config;

import com.shop.product.entity.Product;
import com.shop.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository repo;

    @Override
    public void run(String... args) {
        if (repo.count() > 0) {
            return;
        }
        repo.saveAll(List.of(
                make("iPhone 15", "128GB smartphone", "phones", "69999.00", 25),
                make("Galaxy S24", "256GB smartphone", "phones", "74999.00", 30),
                make("Sony WH-1000XM5", "Noise cancelling headphones", "audio", "29990.00", 40),
                make("Logitech MX Master 3S", "Wireless mouse", "accessories", "9995.00", 60),
                make("Dell XPS 13", "13-inch laptop", "laptops", "114990.00", 10)));
    }

    private Product make(String name, String description, String category, String price, int stock) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setCategory(category);
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        return p;
    }
}