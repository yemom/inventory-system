package com.inventory.backend.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String phone;
    private String gender;
    private LocalDate dateOfBirth;
    private String address;
    private String profilePhotoUrl;
    private String employeeId;
    private String jobTitle;
    private String department;
    private String branch;
    private String warehouse;
    private LocalDate dateJoined;
    private String role;
    private List<String> permissions;
    private String status;
    private boolean passwordResetRequired;
    private boolean isDeleted;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
