package com.inventory.backend.model;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String reference;
    private String orderReference; // PO- or SO-
    private String type; // IN or OUT
    private BigDecimal amount;
    private String paymentMethod;
    
    @Builder.Default
    private String status = "COMPLETED";
    
    private String createdBy;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
}
