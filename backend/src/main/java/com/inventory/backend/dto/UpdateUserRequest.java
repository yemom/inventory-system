package com.inventory.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import java.time.LocalDate;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class UpdateUserRequest {
    private String firstName;
    private String lastName;
    @Email
    private String email;
    @Pattern(regexp = "^[+()0-9\\s-]{7,20}$", message = "must be a valid phone number")
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
    private String roleName;
    private String status;
}
