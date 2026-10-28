package com.inventory.backend.repository;

import com.inventory.backend.model.PayrollProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PayrollProfileRepository extends JpaRepository<PayrollProfile, Long> {
    List<PayrollProfile> findByActiveTrue();
}
