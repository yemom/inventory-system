package com.inventory.backend.repository;

import com.inventory.backend.model.TaxRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TaxRuleRepository extends JpaRepository<TaxRule, Long> {
    List<TaxRule> findByActiveTrueOrderByTypeAscEffectiveFromDesc();
    @Query("""
            select r from TaxRule r where r.code = :code and r.active = true
              and r.effectiveFrom <= :onDate
              and (r.effectiveTo is null or r.effectiveTo >= :onDate)
            order by r.effectiveFrom desc
            """)
    List<TaxRule> findEffective(@Param("code") String code, @Param("onDate") LocalDate onDate);
}
