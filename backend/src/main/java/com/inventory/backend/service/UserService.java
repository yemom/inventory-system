package com.inventory.backend.service;

import com.inventory.backend.dto.*;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public Page<UserDTO> listUsers(String search, Pageable pageable) {
        return listStaff(search, null, null, null, null, null, pageable);
    }

    public Page<UserDTO> listStaff(String search, String role, String department, String branch,
                                   String warehouse, String status, Pageable pageable) {
        return userRepo.findStaff(
                clean(search),
                clean(role),
                clean(department),
                clean(branch),
                clean(warehouse),
                parseStatus(status),
                pageable
        ).map(this::toDTO);
    }

    public StaffStatsDTO getStaffStats() {
        return StaffStatsDTO.builder()
                .totalStaff(userRepo.countByIsDeletedFalse())
                .activeStaff(userRepo.countByIsDeletedFalseAndStatus(UserStatus.ACTIVE))
                .inactiveStaff(userRepo.countByIsDeletedFalseAndStatus(UserStatus.INACTIVE))
                .managers(userRepo.countByIsDeletedFalseAndRoleName("MANAGER"))
                .supervisors(userRepo.countByIsDeletedFalseAndRoleName("SUPERVISOR"))
                .cashiers(userRepo.countByIsDeletedFalseAndRoleName("CASHIER"))
                .inventoryStaff(
                        userRepo.countByIsDeletedFalseAndRoleName("INVENTORY_STAFF")
                                + userRepo.countByIsDeletedFalseAndRoleName("STOREKEEPER"))
                .build();
    }

    public UserDTO getUser(Long id) {
        return toDTO(findById(id));
    }

    public UserDTO createUser(CreateUserRequest req) {
        return createUser(req, null);
    }

    public UserDTO createUser(CreateUserRequest req, User actor) {
        String username = StringUtils.hasText(req.getUsername()) ? req.getUsername().trim() : req.getEmail().trim();
        if (userRepo.existsByUsername(username))
            throw new IllegalArgumentException("Username already exists");
        if (userRepo.existsByEmail(req.getEmail()))
            throw new IllegalArgumentException("Email address is already registered.");
        if (StringUtils.hasText(req.getEmployeeId()) && userRepo.existsByEmployeeId(req.getEmployeeId()))
            throw new IllegalArgumentException("Employee ID already exists.");
        String requestedRole = normalizeRole(req.getRoleName());
        if ("SUPER_ADMIN".equals(requestedRole)) {
            throw new IllegalArgumentException("Super Admin staff accounts cannot be created from staff management.");
        }
        Role role = roleRepo.findByName(requestedRole)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + req.getRoleName()));
        User user = User.builder()
                .username(username)
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .email(req.getEmail())
                .phone(req.getPhone())
                .gender(req.getGender())
                .dateOfBirth(req.getDateOfBirth())
                .address(req.getAddress())
                .profilePhotoUrl(req.getProfilePhotoUrl())
                .employeeId(req.getEmployeeId())
                .jobTitle(req.getJobTitle())
                .department(req.getDepartment())
                .branch(req.getBranch())
                .warehouse(req.getWarehouse())
                .dateJoined(req.getDateJoined())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .passwordResetRequired(true)
                .role(role)
                .status(parseStatus(req.getStatus()) == null ? UserStatus.ACTIVE : parseStatus(req.getStatus()))
                .build();
        User saved = userRepo.save(user);
        log(actor, "STAFF_CREATED", saved, "Created staff member " + saved.getEmployeeId());
        return toDTO(saved);
    }

    public UserDTO updateUser(Long id, UpdateUserRequest req) {
        return updateUser(id, req, null);
    }

    public UserDTO updateUser(Long id, UpdateUserRequest req, User actor) {
        User user = findById(id);
        String previousRole = user.getRole() != null ? user.getRole().getName() : null;
        protectSuperAdminRole(user, req.getRoleName());
        if (req.getEmail() != null && userRepo.existsByEmailAndIdNot(req.getEmail(), id))
            throw new IllegalArgumentException("Email address is already registered.");
        if (req.getEmployeeId() != null && userRepo.existsByEmployeeIdAndIdNot(req.getEmployeeId(), id))
            throw new IllegalArgumentException("Employee ID already exists.");
        if (req.getFirstName() != null) user.setFirstName(req.getFirstName());
        if (req.getLastName() != null) user.setLastName(req.getLastName());
        if (req.getEmail() != null) user.setEmail(req.getEmail());
        if (req.getPhone() != null) user.setPhone(req.getPhone());
        if (req.getGender() != null) user.setGender(req.getGender());
        if (req.getDateOfBirth() != null) user.setDateOfBirth(req.getDateOfBirth());
        if (req.getAddress() != null) user.setAddress(req.getAddress());
        if (req.getProfilePhotoUrl() != null) user.setProfilePhotoUrl(req.getProfilePhotoUrl());
        if (req.getEmployeeId() != null) user.setEmployeeId(req.getEmployeeId());
        if (req.getJobTitle() != null) user.setJobTitle(req.getJobTitle());
        if (req.getDepartment() != null) user.setDepartment(req.getDepartment());
        if (req.getBranch() != null) user.setBranch(req.getBranch());
        if (req.getWarehouse() != null) user.setWarehouse(req.getWarehouse());
        if (req.getDateJoined() != null) user.setDateJoined(req.getDateJoined());
        if (req.getStatus() != null) user.setStatus(requireStatus(req.getStatus()));
        if (req.getRoleName() != null) {
            Role role = roleRepo.findByName(normalizeRole(req.getRoleName()))
                    .orElseThrow(() -> new IllegalArgumentException("Role not found"));
            user.setRole(role);
        }
        User saved = userRepo.save(user);
        String newRole = saved.getRole() != null ? saved.getRole().getName() : null;
        log(actor, previousRole != null && !previousRole.equals(newRole) ? "STAFF_ROLE_UPDATED" : "STAFF_UPDATED",
                saved, "Updated staff member " + saved.getEmployeeId());
        return toDTO(saved);
    }

    public void activateUser(Long id) {
        changeStatus(id, UserStatus.ACTIVE, null);
    }

    public void deactivateUser(Long id) {
        changeStatus(id, UserStatus.INACTIVE, null);
    }

    public void changeStatus(Long id, UserStatus status, User actor) {
        User u = findById(id);
        if ("SUPER_ADMIN".equals(roleName(u)) && status != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("Super Admin accounts cannot be deactivated.");
        }
        u.setStatus(status);
        userRepo.save(u);
        log(actor, status == UserStatus.ACTIVE ? "STAFF_ACTIVATED" : "STAFF_DEACTIVATED",
                u, "Changed staff status to " + status.name());
    }

    public void changeRole(Long id, String roleName) {
        UpdateUserRequest req = UpdateUserRequest.builder().roleName(roleName).build();
        updateUser(id, req, null);
    }

    public void softDeleteUser(Long id, User actor) {
        User u = findById(id);
        if (actor != null && actor.getId().equals(u.getId())) {
            throw new IllegalArgumentException("You cannot remove your own account.");
        }
        if ("SUPER_ADMIN".equals(roleName(u))) {
            throw new IllegalArgumentException("Super Admin accounts cannot be removed from staff management.");
        }
        u.setIsDeleted(true);
        u.setDeletedAt(LocalDateTime.now());
        u.setDeletedBy(actor != null ? actor.getId() : null);
        u.setStatus(UserStatus.INACTIVE);
        userRepo.save(u);
        log(actor, "STAFF_REMOVED", u, "Soft removed staff member " + u.getEmployeeId());
    }

    public void resetPassword(Long id, String temporaryPassword, User actor) {
        if (!StringUtils.hasText(temporaryPassword) || temporaryPassword.length() < 8) {
            throw new IllegalArgumentException("Temporary password must be at least 8 characters.");
        }
        User u = findById(id);
        u.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        u.setPasswordResetRequired(true);
        userRepo.save(u);
        log(actor, "STAFF_PASSWORD_RESET", u, "Reset temporary password for " + u.getEmployeeId());
    }

    public void updateLastLogin(String identifier) {
        userRepo.findByEmail(identifier)
                .or(() -> userRepo.findByUsername(identifier))
                .ifPresent(u -> { u.setLastLoginAt(LocalDateTime.now()); userRepo.save(u); });
    }

    private User findById(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }

    private UserDTO toDTO(User u) {
        Set<Permission> permissions = u.getRole() != null ? u.getRole().getPermissions() : Set.of();
        return UserDTO.builder()
                .id(u.getId()).username(u.getUsername())
                .firstName(u.getFirstName()).lastName(u.getLastName())
                .fullName(u.getFullName()).email(u.getEmail()).phone(u.getPhone())
                .gender(u.getGender()).dateOfBirth(u.getDateOfBirth()).address(u.getAddress())
                .profilePhotoUrl(u.getProfilePhotoUrl()).employeeId(u.getEmployeeId())
                .jobTitle(u.getJobTitle()).department(u.getDepartment()).branch(u.getBranch())
                .warehouse(u.getWarehouse()).dateJoined(u.getDateJoined())
                .role(u.getRole() != null ? u.getRole().getName() : "UNASSIGNED")
                .permissions(permissions.stream().map(Permission::getName).sorted().collect(Collectors.toList()))
                .status(u.getStatus().name())
                .passwordResetRequired(u.getPasswordResetRequired()).isDeleted(u.getIsDeleted())
                .lastLoginAt(u.getLastLoginAt()).createdAt(u.getCreatedAt()).updatedAt(u.getUpdatedAt())
                .build();
    }

    private UserStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) return null;
        return requireStatus(status);
    }

    private UserStatus requireStatus(String status) {
        try {
            return UserStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
    }

    private String normalizeRole(String roleName) {
        if (!StringUtils.hasText(roleName)) {
            throw new IllegalArgumentException("Role is required.");
        }
        return roleName.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void protectSuperAdminRole(User user, String requestedRole) {
        if ("SUPER_ADMIN".equals(roleName(user)) && requestedRole != null && !"SUPER_ADMIN".equals(normalizeRole(requestedRole))) {
            throw new IllegalArgumentException("Super Admin role cannot be changed from staff management.");
        }
        if (requestedRole != null && "SUPER_ADMIN".equals(normalizeRole(requestedRole)) && !"SUPER_ADMIN".equals(roleName(user))) {
            throw new IllegalArgumentException("Staff cannot be promoted to Super Admin from staff management.");
        }
    }

    private String roleName(User user) {
        return user.getRole() != null ? user.getRole().getName() : null;
    }

    private void log(User actor, String action, User target, String details) {
        if (actor == null) return;
        auditLogService.log(actor.getId(), actor.getUsername(), action, "USER", String.valueOf(target.getId()), details);
    }
}
