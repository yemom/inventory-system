package com.inventory.backend.repository;

import com.inventory.backend.model.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    @Query("SELECT jl FROM JournalLine jl JOIN jl.journalEntry je " +
           "WHERE jl.account.id = :accountId AND je.status = 'POSTED' " +
           "AND je.entryDate BETWEEN :start AND :end " +
           "ORDER BY je.entryDate ASC, je.id ASC, jl.lineNumber ASC")
    List<JournalLine> findPostedLinesForAccountBetween(
            @Param("accountId") Long accountId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("SELECT jl FROM JournalLine jl JOIN jl.journalEntry je " +
           "WHERE je.status = 'POSTED' AND je.entryDate BETWEEN :start AND :end " +
           "ORDER BY jl.account.code ASC, je.entryDate ASC")
    List<JournalLine> findAllPostedLinesBetween(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("SELECT jl FROM JournalLine jl JOIN jl.journalEntry je " +
           "WHERE je.status = 'POSTED' " +
           "ORDER BY jl.account.code ASC, je.entryDate ASC")
    List<JournalLine> findAllPostedLines();

    @Query("SELECT COALESCE(SUM(jl.debit), 0) - COALESCE(SUM(jl.credit), 0) " +
           "FROM JournalLine jl JOIN jl.journalEntry je " +
           "WHERE jl.account.id = :accountId AND je.status = 'POSTED' AND je.entryDate < :date")
    BigDecimal computeNetBalanceBefore(
            @Param("accountId") Long accountId,
            @Param("date") LocalDate date);

    boolean existsByAccountId(Long accountId);
}
