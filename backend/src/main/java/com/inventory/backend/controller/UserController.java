package com.inventory.backend.controller;

import com.inventory.backend.dto.*;
import com.inventory.backend.model.UserStatus;
import com.inventory.backend.security.CustomUserDetails;
import com.inventory.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService service;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<UserDTO>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String warehouse,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(service.listStaff(search, role, department, branch, warehouse, status, pageable)));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<StaffStatsDTO>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(service.getStaffStats()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<ApiResponse<UserDTO>> create(@Valid @RequestBody CreateUserRequest req,
                                                       @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Staff member created successfully.", service.createUser(req, principal.getUser())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<UserDTO>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getUser(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<UserDTO>> update(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateUserRequest req,
                                                       @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Staff information updated successfully.", service.updateUser(id, req, principal.getUser())));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id,
                                                      @AuthenticationPrincipal CustomUserDetails principal) {
        service.changeStatus(id, UserStatus.ACTIVE, principal.getUser());
        return ResponseEntity.ok(ApiResponse.ok("Staff account activated successfully.", null));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id,
                                                        @AuthenticationPrincipal CustomUserDetails principal) {
        service.changeStatus(id, UserStatus.INACTIVE, principal.getUser());
        return ResponseEntity.ok(ApiResponse.ok("Staff account deactivated successfully.", null));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> changeStatus(@PathVariable Long id,
                                                          @RequestBody Map<String, String> body,
                                                          @AuthenticationPrincipal CustomUserDetails principal) {
        service.changeStatus(id, UserStatus.valueOf(body.getOrDefault("status", "").toUpperCase()), principal.getUser());
        return ResponseEntity.ok(ApiResponse.ok("Staff account status updated successfully.", null));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<UserDTO>> changeRole(@PathVariable Long id,
                                                           @RequestBody Map<String, String> body,
                                                           @AuthenticationPrincipal CustomUserDetails principal) {
        UpdateUserRequest req = UpdateUserRequest.builder().roleName(body.get("roleName")).build();
        return ResponseEntity.ok(ApiResponse.ok("Role updated successfully.", service.updateUser(id, req, principal.getUser())));
    }

    @PatchMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable Long id,
                                                           @RequestBody Map<String, String> body,
                                                           @AuthenticationPrincipal CustomUserDetails principal) {
        service.resetPassword(id, body.get("temporaryPassword"), principal.getUser());
        return ResponseEntity.ok(ApiResponse.ok("Staff password reset successfully.", null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DEACTIVATE') or hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable Long id,
                                                    @AuthenticationPrincipal CustomUserDetails principal) {
        service.softDeleteUser(id, principal.getUser());
        return ResponseEntity.ok(ApiResponse.ok("Staff member removed successfully.", null));
    }
}
