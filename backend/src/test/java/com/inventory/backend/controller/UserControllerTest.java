package com.inventory.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.backend.dto.CreateUserRequest;
import com.inventory.backend.dto.UpdateUserRequest;
import com.inventory.backend.dto.UserDTO;
import com.inventory.backend.model.Permission;
import com.inventory.backend.model.Role;
import com.inventory.backend.model.User;
import com.inventory.backend.model.UserStatus;
import com.inventory.backend.security.CustomUserDetails;
import com.inventory.backend.security.JwtAuthenticationFilter;
import com.inventory.backend.security.JwtUtil;
import com.inventory.backend.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private UserDTO sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = UserDTO.builder()
                .id(1L)
                .username("test_user")
                .firstName("Test")
                .lastName("User")
                .fullName("Test User")
                .email("test@example.com")
                .role("CASHIER")
                .status("ACTIVE")
                .build();

        // Provide an authenticated principal for @AuthenticationCustomUserDetails
        // resolution. Without this the controller's principal.getUser() NPEs (500).
        Set<Permission> permissions = new HashSet<>();
        Role role = Role.builder()
                .id(1L)
                .name("SUPER_ADMIN")
                .description("Super Admin")
                .permissions(permissions)
                .build();
        User adminUser = User.builder()
                .id(1L)
                .username("admin")
                .firstName("Super")
                .lastName("Admin")
                .email("admin@example.com")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(adminUser);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/users - Returns page of users")
    void listUsers_Success() throws Exception {
        when(userService.listStaff(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleUser)));

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].username").value("test_user"));
    }

    @Test
    @DisplayName("POST /api/v1/users - Valid request returns 201")
    void createUser_Valid() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("new_staff")
                .firstName("New")
                .lastName("Staff")
                .email("new@example.com")
                .phone("+251911000001")
                .password("Password@123")
                .roleName("CASHIER")
                .employeeId("EMP-001")
                .dateJoined(LocalDate.now())
                .build();

        when(userService.createUser(any(CreateUserRequest.class), any(User.class)))
                .thenReturn(sampleUser);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Staff member created successfully."));
    }

    @Test
    @DisplayName("POST /api/v1/users - Validation fails for blank fields returns 400")
    void createUser_ValidationFailure() throws Exception {
        CreateUserRequest invalidRequest = CreateUserRequest.builder()
                .username("")
                .email("not-an-email")
                .build();

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/{id}/deactivate - Deactivates user")
    void deactivateUser_Success() throws Exception {
        doNothing().when(userService)
                .changeStatus(eq(1L), eq(UserStatus.INACTIVE), any(User.class));

        mockMvc.perform(patch("/api/v1/users/1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Staff account deactivated successfully."));

        verify(userService).changeStatus(eq(1L), eq(UserStatus.INACTIVE), any(User.class));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/{id}/role - Changes role")
    void changeRole_Success() throws Exception {
        when(userService.updateUser(eq(1L), any(UpdateUserRequest.class), any(User.class)))
                .thenReturn(sampleUser);

        mockMvc.perform(patch("/api/v1/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleName", "MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Role updated successfully."));

        verify(userService).updateUser(eq(1L), any(UpdateUserRequest.class), any(User.class));
    }
}
