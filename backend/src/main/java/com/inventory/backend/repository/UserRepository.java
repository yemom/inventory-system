package com.inventory.backend.repository;

import com.inventory.backend.model.User;
import com.inventory.backend.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmployeeId(String employeeId);
    boolean existsByEmailAndIdNot(String email, Long id);
    boolean existsByUsernameAndIdNot(String username, Long id);
    boolean existsByEmployeeIdAndIdNot(String employeeId, Long id);

    @Query("""
            select u from User u
            left join u.role r
            where u.isDeleted = false
              and (:search is null or :search = ''
                   or lower(concat(u.firstName, ' ', u.lastName)) like lower(concat('%', :search, '%'))
                   or lower(u.employeeId) like lower(concat('%', :search, '%'))
                   or lower(u.email) like lower(concat('%', :search, '%'))
                   or lower(u.phone) like lower(concat('%', :search, '%')))
              and (:role is null or :role = '' or r.name = :role)
              and (:department is null or :department = '' or u.department = :department)
              and (:branch is null or :branch = '' or u.branch = :branch)
              and (:warehouse is null or :warehouse = '' or u.warehouse = :warehouse)
              and (:status is null or u.status = :status)
            """)
    Page<User> findStaff(
            @Param("search") String search,
            @Param("role") String role,
            @Param("department") String department,
            @Param("branch") String branch,
            @Param("warehouse") String warehouse,
            @Param("status") UserStatus status,
            Pageable pageable);

    long countByIsDeletedFalse();
    long countByIsDeletedFalseAndStatus(UserStatus status);
    long countByIsDeletedFalseAndRoleName(String roleName);
}
