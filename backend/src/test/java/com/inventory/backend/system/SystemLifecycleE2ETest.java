package com.inventory.backend.system;

import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.dto.AuthRequest;
import com.inventory.backend.dto.CreateCustomerRequest;
import com.inventory.backend.dto.CreateUserRequest;
import com.inventory.backend.model.User;
import com.inventory.backend.model.UserStatus;
import com.inventory.backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SystemLifecycleE2ETest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("E2E System Test: Super Admin provisions Cashier -> Cashier creates Customer -> Cashier blocked from creating Users (RBAC) -> Super Admin deactivates Cashier -> Cashier login denied")
    void fullSystemLifecycleWorkflow() throws Exception {
        // Step 1: Super Admin logs in
        String adminToken = loginAsSuperAdmin();
        assertThat(adminToken).isNotBlank();

        // Step 2: Super Admin provisions a new Cashier account
        CreateUserRequest newCashierReq = CreateUserRequest.builder()
                .username("e2e_cashier")
                .firstName("Michael")
                .lastName("Kassaye")
                .email("michael.k@stockflow.local")
                .phone("+251911778899")
                .password("CashierPass@2026!")
                .roleName("CASHIER")
                .build();

        mockMvc.perform(post("/api/v1/users")
                        .headers(authHeader(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCashierReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("e2e_cashier"))
                .andExpect(jsonPath("$.data.role").value("CASHIER"));

        User cashierUser = userRepository.findByUsername("e2e_cashier").orElseThrow();
        Long cashierId = cashierUser.getId();

        // Step 3: Cashier logs in with their newly provisioned credentials
        String cashierToken = login("e2e_cashier", "CashierPass@2026!");
        assertThat(cashierToken).isNotBlank();

        // Step 4: Cashier successfully creates a Customer (authorized by CUSTOMER_CREATE)
        CreateCustomerRequest custReq = CreateCustomerRequest.builder()
                .name("E2E Walk-in Client")
                .phone("+251922334455")
                .customerType("WALK_IN")
                .creditLimit(BigDecimal.ZERO)
                .build();

        mockMvc.perform(post("/api/v1/customers")
                        .headers(authHeader(cashierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("E2E Walk-in Client"));

        // Step 5: Cashier attempts to create a User (RBAC check: Cashier lacks USER_CREATE authority)
        CreateUserRequest unauthorizedUserReq = CreateUserRequest.builder()
                .username("rogue_user")
                .firstName("Rogue")
                .lastName("User")
                .email("rogue@stockflow.local")
                .password("Password@123")
                .roleName("CASHIER")
                .build();

        mockMvc.perform(post("/api/v1/users")
                        .headers(authHeader(cashierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unauthorizedUserReq)))
                .andExpect(status().isForbidden()); // 403 Forbidden verified!

        // Step 6: Super Admin deactivates Cashier
        mockMvc.perform(patch("/api/v1/users/" + cashierId + "/deactivate")
                        .headers(authHeader(adminToken)))
                .andExpect(status().isOk());

        User deactivatedCashier = userRepository.findById(cashierId).orElseThrow();
        assertThat(deactivatedCashier.getStatus()).isEqualTo(UserStatus.INACTIVE);

        // Step 7: Deactivated Cashier attempts to log in again -> denied (cannot authenticate inactive user)
        AuthRequest loginAttempt = new AuthRequest("e2e_cashier", "CashierPass@2026!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginAttempt)))
                .andExpect(status().isUnauthorized());
    }
}