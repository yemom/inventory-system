package com.inventory.backend.repository;

import com.inventory.backend.model.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    Optional<JournalEntry> findByEntryNumber(String entryNumber);
    boolean existsBySourceTypeAndSourceId(String sourceType, String sourceId);
    List<JournalEntry> findBySourceTypeAndSourceId(String sourceType, String sourceId);

    List<JournalEntry> findByEntryDateBetweenOrderByEntryDateDescIdDesc(LocalDate start, LocalDate end);
    List<JournalEntry> findByAccountingPeriodOrderByEntryDateDescIdDesc(String accountingPeriod);
    List<JournalEntry> findByStatusOrderByEntryDateDescIdDesc(String status);
    List<JournalEntry> findAllByOrderByEntryDateDescIdDesc();

    @Query("SELECT COUNT(j) FROM JournalEntry j WHERE j.status = 'POSTED'")
    long countPosted();

    @Query("SELECT COUNT(j) FROM JournalEntry j WHERE j.status = 'DRAFT'")
    long countDraft();
}
