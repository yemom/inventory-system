package com.inventory.backend.service;

import com.inventory.backend.model.AuditLog;
import com.inventory.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogRepository repo;

    @Async
    public void log(Long userId, String username, String action,
                    String entityType, String entityId, String details) {
        repo.save(AuditLog.builder()
                .userId(userId).username(username).action(action)
                .entityType(entityType).entityId(entityId).newValue(details)
                .build());
    }

    public List<AuditLog> getRecent() { return repo.findTop100ByOrderByTimestampDesc(); }
    public List<AuditLog> getByUser(Long userId) { return repo.findByUserIdOrderByTimestampDesc(userId); }
}
