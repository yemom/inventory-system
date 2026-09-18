package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String username;

    @Column(nullable = false)
    private String action;

    private String entityType;
    private String entityId;

    @Lob
    private String previousValue;

    @Lob
    private String newValue;

    private String ipAddress;

    @CreationTimestamp
    private LocalDateTime timestamp;
}
