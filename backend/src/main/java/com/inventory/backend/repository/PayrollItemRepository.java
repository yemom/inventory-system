package com.inventory.backend.repository;

import com.inventory.backend.model.PayrollItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PayrollItemRepository extends JpaRepository<PayrollItem, Long> {
    List<PayrollItem> findByPayrollPeriodId(Long payrollPeriodId);
    boolean existsByPayrollPeriodId(Long payrollPeriodId);
}
