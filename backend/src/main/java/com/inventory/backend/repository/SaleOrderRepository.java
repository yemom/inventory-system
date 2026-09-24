package com.inventory.backend.repository;

import com.inventory.backend.model.SaleOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;

public interface SaleOrderRepository extends JpaRepository<SaleOrder, Long> {

    @Query("SELECT COALESCE(SUM(s.finalAmount), 0) FROM SaleOrder s WHERE s.status <> 'CANCELLED'")
    BigDecimal sumFinalAmount();
}
