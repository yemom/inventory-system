package com.inventory.backend.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class CreateUserRequest {
    private String username;
    @NotBlank private String firstName;
    @NotBlank private String lastName;
    @NotBlank @Email private String email;
    @NotBlank @Pattern(regexp = "^[+()0-9\\s-]{7,20}$", message = "must be a valid phone number")
    private String phone;
    private String gender;
    private LocalDate dateOfBirth;
    private String address;
    private String profilePhotoUrl;
    @NotBlank private String employeeId;
    private String jobTitle;
    private String department;
    private String branch;
    private String warehouse;
    @NotNull private LocalDate dateJoined;
    @NotBlank @Size(min = 8) private String password;
    @NotBlank private String roleName;
    private String status;
}
