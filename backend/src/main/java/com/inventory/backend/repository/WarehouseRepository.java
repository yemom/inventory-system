package com.inventory.backend.repository;

import com.inventory.backend.model.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    boolean existsByName(String name);
    List<Warehouse> findByActiveTrue();
}
