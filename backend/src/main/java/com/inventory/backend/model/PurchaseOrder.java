package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "purchase_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    private String supplierName;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalAmount;

    private String status; // RECEIVED, DRAFT, CANCELLED

    /**
     * Payment status independent of stock-receipt status.
     * Values: PAID, PARTIAL, UNPAID.
     * A purchase can be RECEIVED (goods arrived) but UNPAID (bought on credit).
     */
    @Column(name = "payment_status", length = 20)
    private String paymentStatus; // PAID, PARTIAL, UNPAID

    /**
     * Amount already paid against this purchase order.
     * Null or zero means nothing has been paid yet.
     */
    @Column(name = "amount_paid", precision = 10, scale = 2)
    private BigDecimal amountPaid;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL)
    private List<PurchaseOrderItem> items;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
