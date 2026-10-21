package com.shop.product.repository;

import com.shop.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByActiveTrueAndNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Product> findByActiveTrueAndCategoryIgnoreCase(String category, Pageable pageable);

    Page<Product> findByActiveTrueAndCategoryIgnoreCaseAndNameContainingIgnoreCase(
            String category, String name, Pageable pageable);

    // One atomic statement: it can never take stock below zero, even with many concurrent buyers.
    @Modifying
    @Query("update Product p set p.stock = p.stock - :qty, p.version = p.version + 1 "
            + "where p.id = :id and p.active = true and p.stock >= :qty")
    int decrementStock(@Param("id") Long id, @Param("qty") int qty);

    @Modifying
    @Query("update Product p set p.stock = p.stock + :qty, p.version = p.version + 1 where p.id = :id")
    int incrementStock(@Param("id") Long id, @Param("qty") int qty);
}