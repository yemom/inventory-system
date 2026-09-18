package com.inventory.backend.repository;

import com.inventory.backend.model.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Optional<Supplier> findBySupplierNumber(String supplierNumber);
    boolean existsBySupplierNumber(String supplierNumber);
    Page<Supplier> findByCompanyNameContainingIgnoreCase(String name, Pageable pageable);
}
