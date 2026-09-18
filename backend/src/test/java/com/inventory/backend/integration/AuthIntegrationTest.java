package com.inventory.backend.integration;

import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.dto.AuthRequest;
import com.inventory.backend.dto.AuthResponse;
import com.inventory.backend.model.User;
import com.inventory.backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Integration: Login with seeded Super Admin credentials")
    void login_SeededSuperAdmin_Success() throws Exception {
        AuthRequest req = new AuthRequest("superadmin", "Admin@StockFlow2026!");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("superadmin"))
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.permissions").isArray())
                .andReturn();

        AuthResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(resp.getToken()).isNotBlank();
        assertThat(resp.getPermissions()).contains("USER_CREATE", "CUSTOMER_CREATE", "SUPPLIER_CREATE");

        // Verify lastLoginAt timestamp updated in real DB
        User user = userRepository.findByUsername("superadmin").orElseThrow();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("Integration: Login with invalid password returns 401 Unauthorized")
    void login_InvalidPassword_Returns401() throws Exception {
        AuthRequest req = new AuthRequest("superadmin", "WrongPassword!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Integration: Login with non-existent user returns 401 Unauthorized")
    void login_NonExistentUser_Returns401() throws Exception {
        AuthRequest req = new AuthRequest("non_existent_user", "Password@123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }
}