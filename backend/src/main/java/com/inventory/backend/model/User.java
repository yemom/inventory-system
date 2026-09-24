package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    private String gender;

    private LocalDate dateOfBirth;

    @Column(length = 1000)
    private String address;

    private String profilePhotoUrl;

    @Column(unique = true)
    private String employeeId;

    private String jobTitle;

    private String department;

    private String branch;

    private String warehouse;

    private LocalDate dateJoined;

    @Column(nullable = false)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    private LocalDateTime lastLoginAt;

    @Builder.Default
    @Column(columnDefinition = "boolean default false")
    private Boolean passwordResetRequired = false;

    @Builder.Default
    @Column(columnDefinition = "boolean default false")
    private Boolean isDeleted = false;

    private LocalDateTime deletedAt;

    private Long deletedBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public String getFullName() { return firstName + " " + lastName; }
    public boolean isActive() { return status == UserStatus.ACTIVE; }
}
