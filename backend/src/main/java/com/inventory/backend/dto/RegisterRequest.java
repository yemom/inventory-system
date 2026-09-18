package com.inventory.backend.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class RegisterRequest {
    @NotBlank private String username;
    @NotBlank private String firstName;
    @NotBlank private String lastName;
    @NotBlank @Email private String email;
    private String phone;
    @NotBlank @Size(min = 8) private String password;
}