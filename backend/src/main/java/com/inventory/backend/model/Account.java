package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "accounts", indexes = {
        @Index(name = "idx_accounts_category", columnList = "category"),
        @Index(name = "idx_accounts_code", columnList = "code"),
        @Index(name = "idx_accounts_active", columnList = "active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    /** ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE */
    @Column(nullable = false, length = 50)
    private String category;

    /** CURRENT_ASSET, CURRENT_LIABILITY, COGS, OPERATING_EXPENSE, etc. */
    @Column(name = "sub_category", length = 100)
    private String subCategory;

    @Column(length = 500)
    private String description;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    /** System accounts cannot be deleted as they are mapped to automatic operational entries */
    @Builder.Default
    @Column(name = "system_account", nullable = false)
    private Boolean systemAccount = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
