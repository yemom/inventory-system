package com.inventory.backend.repository;

import com.inventory.backend.model.AiQueryLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiQueryLogRepository extends JpaRepository<AiQueryLog, Long> {
    Page<AiQueryLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}