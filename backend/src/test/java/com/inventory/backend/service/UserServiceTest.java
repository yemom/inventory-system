package com.inventory.backend.service;

import com.inventory.backend.dto.CreateUserRequest;
import com.inventory.backend.dto.UpdateUserRequest;
import com.inventory.backend.dto.UserDTO;
import com.inventory.backend.model.Role;
import com.inventory.backend.model.User;
import com.inventory.backend.model.UserStatus;
import com.inventory.backend.repository.RoleRepository;
import com.inventory.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private Role cashierRole;
    private User testUser;

    @BeforeEach
    void setUp() {
        cashierRole = Role.builder().id(1L).name("CASHIER").description("Cashier Role").build();
        testUser = User.builder()
                .id(1L)
                .username("john_doe")
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .passwordHash("encoded_pwd")
                .role(cashierRole)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Create User - Success")
    void createUser_Success() {
        CreateUserRequest req = CreateUserRequest.builder()
                .username("new_staff")
                .firstName("New")
                .lastName("Staff")
                .email("staff@stockflow.com")
                .password("Password@123")
                .roleName("CASHIER")
                .build();

        when(userRepository.existsByUsername(req.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(roleRepository.findByName("CASHIER")).thenReturn(Optional.of(cashierRole));
        when(passwordEncoder.encode(req.getPassword())).thenReturn("encoded_pass");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(2L);
            return u;
        });

        UserDTO created = userService.createUser(req);

        assertThat(created).isNotNull();
        assertThat(created.getUsername()).isEqualTo("new_staff");
        assertThat(created.getFullName()).isEqualTo("New Staff");
        assertThat(created.getRole()).isEqualTo("CASHIER");
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Create User - Duplicate Username Throws Exception")
    void createUser_DuplicateUsername() {
        CreateUserRequest req = CreateUserRequest.builder()
                .username("john_doe")
                .firstName("John")
                .lastName("Doe")
                .email("other@example.com")
                .password("Password@123")
                .roleName("CASHIER")
                .build();

        when(userRepository.existsByUsername("john_doe")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already exists");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Create User - Duplicate Email Throws Exception")
    void createUser_DuplicateEmail() {
        CreateUserRequest req = CreateUserRequest.builder()
                .username("unique_user")
                .firstName("Unique")
                .lastName("User")
                .email("john@example.com")
                .password("Password@123")
                .roleName("CASHIER")
                .build();

        when(userRepository.existsByUsername("unique_user")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Activate and Deactivate User")
    void activateAndDeactivateUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        userService.deactivateUser(1L);
        assertThat(testUser.getStatus()).isEqualTo(UserStatus.INACTIVE);

        userService.activateUser(1L);
        assertThat(testUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}