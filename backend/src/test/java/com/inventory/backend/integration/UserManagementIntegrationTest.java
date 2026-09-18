package com.inventory.backend.integration;

import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.dto.CreateUserRequest;
import com.inventory.backend.dto.UpdateUserRequest;
import com.inventory.backend.model.User;
import com.inventory.backend.model.UserStatus;
import com.inventory.backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserManagementIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Integration: Super Admin creates staff, lists, deactivates, and updates role")
    void userLifecycle_SuperAdmin_Success() throws Exception {
        String token = loginAsSuperAdmin();

        // 1. Create staff user
        CreateUserRequest createReq = CreateUserRequest.builder()
                .username("sarah_cashier")
                .firstName("Sarah")
                .lastName("Tekle")
                .email("sarah.tekle@stockflow.local")
                .phone("+251911223344")
                .password("Cashier@StockFlow2026!")
                .roleName("CASHIER")
                .build();

        mockMvc.perform(post("/api/v1/users")
                        .headers(authHeader(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("sarah_cashier"))
                .andExpect(jsonPath("$.data.role").value("CASHIER"));

        // Verify in database: password is BCrypt hashed, status is ACTIVE
        User createdUser = userRepository.findByUsername("sarah_cashier").orElseThrow();
        assertThat(createdUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(passwordEncoder.matches("Cashier@StockFlow2026!", createdUser.getPasswordHash())).isTrue();

        Long staffId = createdUser.getId();

        // 2. List users - should include the new staff
        mockMvc.perform(get("/api/v1/users")
                        .headers(authHeader(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        // 3. Deactivate staff
        mockMvc.perform(patch("/api/v1/users/" + staffId + "/deactivate")
                        .headers(authHeader(token)))
                .andExpect(status().isOk());

        User deactivated = userRepository.findById(staffId).orElseThrow();
        assertThat(deactivated.getStatus()).isEqualTo(UserStatus.INACTIVE);

        // 4. Reactivate staff
        mockMvc.perform(patch("/api/v1/users/" + staffId + "/activate")
                        .headers(authHeader(token)))
                .andExpect(status().isOk());

        User reactivated = userRepository.findById(staffId).orElseThrow();
        assertThat(reactivated.getStatus()).isEqualTo(UserStatus.ACTIVE);

        // 5. Change role to MANAGER
        mockMvc.perform(patch("/api/v1/users/" + staffId + "/role")
                        .headers(authHeader(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleName", "MANAGER"))))
                .andExpect(status().isOk());

        User promoted = userRepository.findById(staffId).orElseThrow();
        assertThat(promoted.getRole().getName()).isEqualTo("MANAGER");
    }

    @Test
    @DisplayName("Integration: Duplicate username or email rejected with 400")
    void createUser_DuplicateRejected() throws Exception {
        String token = loginAsSuperAdmin();

        CreateUserRequest duplicateReq = CreateUserRequest.builder()
                .username("superadmin") // already seeded
                .firstName("Another")
                .lastName("Admin")
                .email("another@stockflow.local")
                .password("Password@123")
                .roleName("SUPER_ADMIN")
                .build();

        mockMvc.perform(post("/api/v1/users")
                        .headers(authHeader(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}