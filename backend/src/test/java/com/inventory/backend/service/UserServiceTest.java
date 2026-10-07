package com.inventory.backend.service;

import com.inventory.backend.dto.CreateUserRequest;
import com.inventory.backend.dto.UpdateUserRequest;
import com.inventory.backend.dto.UserDTO;
import com.inventory.backend.model.Role;
import com.inventory.backend.model.User;
import com.inventory.backend.model.UserStatus;
import com.inventory.backend.repository.RoleRepository;
import com.inventory.backend.repository.UserRepository;
import com.inventory.backend.security.RoleGrantPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.inventory.backend.repository.AuditLogRepository;
import org.mockito.Spy;
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

    @Mock
    private AuditLogRepository auditLogRepository;

    // Built by hand rather than with @InjectMocks so the real RoleGrantPolicy is
    // used: createUser must actually enforce the role-grant rule, and a mock
    // would pass whether or not the rule works.
    private UserService userService;

    private Role cashierRole;
    private Role managerRole;
    private User managerActor;
    private User testUser;

    @BeforeEach
    void setUp() {
        managerRole = Role.builder().id(9L).name("MANAGER").description("Manager Role").build();
        // A Manager may create Cashiers, Supervisors and Inventory Staff, so this
        // actor is what the create tests below exercise the happy path with.
        managerActor = User.builder()
                .id(99L).username("manager_actor").email("manager_actor@stockflow.test")
                .role(managerRole).status(UserStatus.ACTIVE).build();

        userService = new UserService(userRepository, roleRepository, passwordEncoder,
                new AuditLogService(auditLogRepository), new RoleGrantPolicy());

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

        UserDTO created = userService.createUser(req, managerActor);

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

        assertThatThrownBy(() -> userService.createUser(req, managerActor))
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

        assertThatThrownBy(() -> userService.createUser(req, managerActor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email address is already registered");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Activate and Deactivate User")
    void activateAndDeactivateUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        userService.changeStatus(1L, UserStatus.INACTIVE, managerActor);
        assertThat(testUser.getStatus()).isEqualTo(UserStatus.INACTIVE);

        userService.changeStatus(1L, UserStatus.ACTIVE, managerActor);
        assertThat(testUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
