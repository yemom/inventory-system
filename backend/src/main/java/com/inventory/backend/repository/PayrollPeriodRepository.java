package com.inventory.backend.repository;

import com.inventory.backend.model.PayrollPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, Long> {
    List<PayrollPeriod> findAllByOrderByYearDescMonthDesc();
}
