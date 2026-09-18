package com.inventory.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthRequest {
    /** Email address OR username — both are accepted at the login endpoint. */
    private String identifier;
    private String password;
}
