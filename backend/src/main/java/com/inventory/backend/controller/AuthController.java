package com.inventory.backend.controller;

import com.inventory.backend.dto.AuthRequest;
import com.inventory.backend.dto.AuthResponse;
import com.inventory.backend.dto.CreateUserRequest;
import com.inventory.backend.dto.RegisterRequest;
import com.inventory.backend.model.Permission;
import com.inventory.backend.model.User;
import com.inventory.backend.repository.UserRepository;
import com.inventory.backend.security.CustomUserDetails;
import com.inventory.backend.security.JwtUtil;
import com.inventory.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        String identifier = request.getIdentifier();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identifier, request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "Invalid credentials. Please try again."));
        }

        final UserDetails userDetails = userDetailsService.loadUserByUsername(identifier);
        final String token = jwtUtil.generateToken(userDetails);

        userService.updateLastLogin(identifier);

        User user = ((CustomUserDetails) userDetails).getUser();
        List<String> permissions = user.getRole().getPermissions().stream()
                .map(Permission::getName).collect(Collectors.toList());

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .name(user.getFullName())
                .role(user.getRole() != null ? user.getRole().getName() : "UNASSIGNED")
                .permissions(permissions)
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.status(409)
                    .body(Map.of("success", false, "message", "An account with this email already exists"));
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.status(409)
                    .body(Map.of("success", false, "message", "This username is already taken"));
        }

        try {
            CreateUserRequest createReq = CreateUserRequest.builder()
                    .username(request.getUsername())
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(request.getEmail())
                    .phone(request.getPhone())
                    .password(request.getPassword())
                    .roleName("CASHIER")
                    .build();

            userService.createUser(createReq);

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
            final UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
            final String token = jwtUtil.generateToken(userDetails);
            userService.updateLastLogin(request.getEmail());

            User user = ((CustomUserDetails) userDetails).getUser();
            List<String> permissions = user.getRole().getPermissions().stream()
                    .map(Permission::getName).collect(Collectors.toList());

            return ResponseEntity.status(201).body(AuthResponse.builder()
                    .token(token)
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .name(user.getFullName())
                    .role(user.getRole() != null ? user.getRole().getName() : "UNASSIGNED")
                    .permissions(permissions)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(400)
                    .body(Map.of("success", false, "message",
                            e.getMessage() != null ? e.getMessage() : "Registration failed"));
        }
    }
}