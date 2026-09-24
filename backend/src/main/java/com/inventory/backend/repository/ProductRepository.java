package com.inventory.backend.repository;

import com.inventory.backend.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    @Query("SELECT p FROM Product p WHERE " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.barcode) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Product> search(@Param("search") String search, Pageable pageable);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    /** Pessimistic write lock – prevents concurrent overselling */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    /** Dashboard aggregates */
    long countByActiveTrue();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.active = true AND p.quantity > 0 AND p.quantity <= COALESCE(p.reorderLevel, 0)")
    long countLowStock();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.active = true AND p.quantity = 0")
    long countOutOfStock();

    @Query("SELECT COALESCE(SUM(p.quantity * p.purchasePrice), 0) FROM Product p WHERE p.active = true")
    BigDecimal totalInventoryValue();
}
