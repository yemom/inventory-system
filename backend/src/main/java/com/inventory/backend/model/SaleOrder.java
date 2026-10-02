package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "sale_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SaleOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    private String customerName;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal discount;

    @Column(precision = 10, scale = 2)
    private BigDecimal tax;

    @Column(precision = 10, scale = 2)
    private BigDecimal finalAmount;

    private String status; // PAID, PENDING, CANCELLED
    private String paymentStatus; // PAID, PARTIAL, UNPAID
    private String paymentMethod; // CASH, CARD, TRANSFER, BANK, MOBILE_MONEY, CREDIT

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Column(unique = true)
    private String idempotencyKey;

    @OneToMany(mappedBy = "saleOrder", cascade = CascadeType.ALL)
    private List<SaleOrderItem> items;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
