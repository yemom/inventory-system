package com.inventory.backend.repository;

import com.inventory.backend.model.TaxReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TaxReceiptRepository extends JpaRepository<TaxReceipt, Long> {
    @Query("select r from TaxReceipt r order by r.createdAt desc")
    List<TaxReceipt> findAllOrderByCreatedAtDesc();
}
