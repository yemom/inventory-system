package com.inventory.backend.dto;

import lombok.*;
import java.util.List;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class AuthResponse {
    private String token;
    private Long id;
    private String username;
    private String email;
    private String name;
    private String role;
    private List<String> permissions;
    /** The user's assigned warehouse name (free-text label on the User entity, may be null). */
    private String warehouse;
}
