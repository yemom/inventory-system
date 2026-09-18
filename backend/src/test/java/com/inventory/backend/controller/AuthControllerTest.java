package com.inventory.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.backend.dto.AuthRequest;
import com.inventory.backend.model.Permission;
import com.inventory.backend.model.Role;
import com.inventory.backend.model.User;
import com.inventory.backend.model.UserStatus;
import com.inventory.backend.repository.UserRepository;
import com.inventory.backend.security.CustomUserDetails;
import com.inventory.backend.security.JwtUtil;
import com.inventory.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    private User testUser;
    private CustomUserDetails customUserDetails;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();

        Role role = Role.builder().id(1L).name("CASHIER").permissions(Set.of(Permission.builder().id(1L).name("SALE_CREATE").build())).build();
        testUser = User.builder()
                .id(10L)
                .username("cashier1")
                .firstName("Cashier")
                .lastName("One")
                .email("cashier1@example.com")
                .passwordHash("hashed")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
        customUserDetails = new CustomUserDetails(testUser);
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Success returns JWT and user profile")
    void login_Success() throws Exception {
        AuthRequest request = new AuthRequest("cashier1", "Password@123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken(customUserDetails, null));
        when(userDetailsService.loadUserByUsername("cashier1")).thenReturn(customUserDetails);
        when(jwtUtil.generateToken(customUserDetails)).thenReturn("mock.jwt.token");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock.jwt.token"))
                .andExpect(jsonPath("$.username").value("cashier1"))
                .andExpect(jsonPath("$.role").value("CASHIER"))
                .andExpect(jsonPath("$.permissions[0]").value("SALE_CREATE"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Bad credentials returns 401")
    void login_BadCredentials() throws Exception {
        AuthRequest request = new AuthRequest("cashier1", "WrongPassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid credentials. Please try again."));
    }
}