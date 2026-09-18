package com.inventory.backend.repository;

import com.inventory.backend.model.Customer;
import com.inventory.backend.model.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByCustomerNumber(String customerNumber);
    boolean existsByCustomerNumber(String customerNumber);
    Page<Customer> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<Customer> findByStatus(CustomerStatus status, Pageable pageable);
}
