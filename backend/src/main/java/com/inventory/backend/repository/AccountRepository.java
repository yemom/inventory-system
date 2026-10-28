package com.inventory.backend.repository;

import com.inventory.backend.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByCode(String code);
    boolean existsByCode(String code);
    List<Account> findByCategory(String category);
    List<Account> findByActiveTrueOrderByCodeAsc();
    List<Account> findAllByOrderByCodeAsc();
}
