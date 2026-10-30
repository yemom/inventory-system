package com.inventory.backend.service;

import com.inventory.backend.dto.AccountingDTO.*;
import com.inventory.backend.dto.PayrollDTO;
import com.inventory.backend.dto.TaxSummaryDTO;
import com.inventory.backend.finance.FinancialCalculationService;
import com.inventory.backend.finance.FinancialSummary;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalLineRepository journalLineRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ExpenseRepository expenseRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final FinancialCalculationService financialCalculationService;
    private final TaxService taxService;
    private final PayrollService payrollService;

    private static final AtomicLong ENTRY_SEQ = new AtomicLong(System.currentTimeMillis() % 10000);

    // ─── Chart of Accounts ───────────────────────────────────────────────────────

    @Transactional
    public void seedStandardAccountsIfEmpty() {
        if (accountRepository.count() > 0) return;

        List<Account> defaults = List.of(
                Account.builder().code("1010").name("Cash on Hand").category("ASSET").subCategory("CURRENT_ASSET").description("Physical cash at registers and safe").systemAccount(true).active(true).build(),
                Account.builder().code("1020").name("Bank Accounts (CBE / Telebirr)").category("ASSET").subCategory("CURRENT_ASSET").description("Commercial bank and mobile money accounts").systemAccount(true).active(true).build(),
                Account.builder().code("1100").name("Accounts Receivable").category("ASSET").subCategory("CURRENT_ASSET").description("Credit sales owed by customers").systemAccount(true).active(true).build(),
                Account.builder().code("1200").name("Merchandise Inventory").category("ASSET").subCategory("CURRENT_ASSET").description("Goods held for resale").systemAccount(true).active(true).build(),
                Account.builder().code("2010").name("Accounts Payable").category("LIABILITY").subCategory("CURRENT_LIABILITY").description("Amounts owed to suppliers for inventory").systemAccount(true).active(true).build(),
                Account.builder().code("2020").name("Output VAT Payable").category("LIABILITY").subCategory("CURRENT_LIABILITY").description("15% Ethiopian VAT collected from customers").systemAccount(true).active(true).build(),
                Account.builder().code("2030").name("Employment Income Tax Payable").category("LIABILITY").subCategory("CURRENT_LIABILITY").description("Withheld salary tax payable to ERCA").systemAccount(true).active(true).build(),
                Account.builder().code("2040").name("Pension Contributions Payable").category("LIABILITY").subCategory("CURRENT_LIABILITY").description("Employee 7% + Employer 11% POESSA pension").systemAccount(true).active(true).build(),
                Account.builder().code("3010").name("Owner's Equity & Retained Earnings").category("EQUITY").subCategory("EQUITY").description("Accumulated business earnings and capital").systemAccount(true).active(true).build(),
                Account.builder().code("4010").name("Sales Revenue").category("REVENUE").subCategory("OPERATING_REVENUE").description("Income from retail and wholesale merchandise sales").systemAccount(true).active(true).build(),
                Account.builder().code("5010").name("Cost of Goods Sold (COGS)").category("EXPENSE").subCategory("COGS").description("Purchase cost of inventory sold").systemAccount(true).active(true).build(),
                Account.builder().code("6010").name("Operating & Administrative Expenses").category("EXPENSE").subCategory("OPERATING_EXPENSE").description("Rent, utilities, office, and logistics costs").systemAccount(true).active(true).build(),
                Account.builder().code("6020").name("Salaries & Payroll Expense").category("EXPENSE").subCategory("OPERATING_EXPENSE").description("Gross employee salaries and employer pension").systemAccount(true).active(true).build()
        );

        accountRepository.saveAll(defaults);
        log.info("Seeded {} standard Ethiopian Chart of Accounts entries", defaults.size());
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts() {
        seedStandardAccountsIfEmpty();
        List<Account> accounts = accountRepository.findAllByOrderByCodeAsc();
        return accounts.stream().map(this::toAccountResponse).collect(Collectors.toList());
    }

    private AccountResponse toAccountResponse(Account a) {
        BigDecimal balance = computeAccountBalance(a.getId(), a.getCategory());
        return new AccountResponse(
                a.getId(), a.getCode(), a.getName(), a.getCategory(),
                a.getSubCategory(), a.getDescription(), a.getActive(),
                a.getSystemAccount(), balance
        );
    }

    private BigDecimal computeAccountBalance(Long accountId, String category) {
        BigDecimal netDebitMinusCredit = journalLineRepository.computeNetBalanceBefore(accountId, LocalDate.now().plusDays(1));
        if (netDebitMinusCredit == null) netDebitMinusCredit = BigDecimal.ZERO;

        if ("ASSET".equalsIgnoreCase(category) || "EXPENSE".equalsIgnoreCase(category)) {
            return netDebitMinusCredit.setScale(2, RoundingMode.HALF_UP);
        } else {
            return netDebitMinusCredit.negate().setScale(2, RoundingMode.HALF_UP);
        }
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest req) {
        if (accountRepository.existsByCode(req.code())) {
            throw new IllegalArgumentException("Account with code " + req.code() + " already exists");
        }
        Account account = Account.builder()
                .code(req.code().trim())
                .name(req.name().trim())
                .category(req.category().trim().toUpperCase())
                .subCategory(req.subCategory() != null ? req.subCategory().trim() : null)
                .description(req.description())
                .active(true)
                .systemAccount(false)
                .build();
        Account saved = accountRepository.save(account);
        return toAccountResponse(saved);
    }

    @Transactional
    public AccountResponse updateAccount(Long id, UpdateAccountRequest req) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Account not found with ID: " + id));
        account.setName(req.name().trim());
        if (req.subCategory() != null) account.setSubCategory(req.subCategory().trim());
        if (req.description() != null) account.setDescription(req.description());
        if (req.active() != null) account.setActive(req.active());
        return toAccountResponse(accountRepository.save(account));
    }

    @Transactional
    public void deleteAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Account not found with ID: " + id));
        if (Boolean.TRUE.equals(account.getSystemAccount())) {
            throw new IllegalStateException("System accounts cannot be deleted as they are mapped to operational workflows");
        }
        if (journalLineRepository.existsByAccountId(id)) {
            throw new IllegalStateException("Account cannot be deleted because it contains posted or drafted journal entries");
        }
        accountRepository.delete(account);
    }

    // ─── Journal Entries ─────────────────────────────────────────────────────────

    @Transactional
    public JournalEntryResponse createJournalEntry(CreateJournalEntryRequest req, User user) {
        seedStandardAccountsIfEmpty();

        if (req.sourceType() != null && req.sourceId() != null
                && journalEntryRepository.existsBySourceTypeAndSourceId(req.sourceType(), req.sourceId())) {
            throw new IllegalArgumentException("A journal already exists for this source transaction.");
        }

        String period = YearMonth.from(req.entryDate()).toString();
        String entryNumber = generateEntryNumber(req.entryDate());

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        JournalEntry entry = JournalEntry.builder()
                .entryNumber(entryNumber)
                .entryDate(req.entryDate())
                .accountingPeriod(period)
                .reference(req.reference())
                .description(req.description())
                .sourceType(req.sourceType() != null ? req.sourceType() : "MANUAL")
                .sourceId(req.sourceId())
                .status("DRAFT")
                .build();

        int lineNum = 1;
        for (CreateJournalLineRequest lineReq : req.lines()) {
            Account account = resolveAccount(lineReq.accountId(), lineReq.accountCode());
            BigDecimal d = lineReq.debit() != null ? lineReq.debit().setScale(2, RoundingMode.HALF_UP) : ZERO;
            BigDecimal c = lineReq.credit() != null ? lineReq.credit().setScale(2, RoundingMode.HALF_UP) : ZERO;

            totalDebit = totalDebit.add(d);
            totalCredit = totalCredit.add(c);

            JournalLine line = JournalLine.builder()
                    .account(account)
                    .debit(d)
                    .credit(c)
                    .description(lineReq.description() != null ? lineReq.description() : req.description())
                    .lineNumber(lineNum++)
                    .build();
            entry.addLine(line);
        }

        entry.setTotalDebit(totalDebit);
        entry.setTotalCredit(totalCredit);

        JournalEntry saved = journalEntryRepository.save(entry);
        return toJournalEntryResponse(saved);
    }

    @Transactional
    public JournalEntryResponse postJournalEntry(Long id, User user) {
        JournalEntry entry = journalEntryRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Journal entry not found: " + id));

        if (!"DRAFT".equals(entry.getStatus())) {
            throw new IllegalStateException("Only DRAFT journal entries can be posted. Current status: " + entry.getStatus());
        }

        if (entry.getTotalDebit().compareTo(entry.getTotalCredit()) != 0) {
            throw new IllegalArgumentException(String.format(
                    "Unbalanced journal entry cannot be posted: Total Debits (ETB %s) != Total Credits (ETB %s)",
                    entry.getTotalDebit(), entry.getTotalCredit()));
        }
        BigDecimal lineDebits = entry.getLines().stream().map(JournalLine::getDebit)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal lineCredits = entry.getLines().stream().map(JournalLine::getCredit)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (lineDebits.compareTo(entry.getTotalDebit()) != 0 || lineCredits.compareTo(entry.getTotalCredit()) != 0) {
            throw new IllegalStateException("Journal header totals do not match its lines; entry cannot be posted.");
        }
        if (entry.getTotalDebit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Journal entry amount must be greater than zero");
        }

        entry.setStatus("POSTED");
        entry.setPostedAt(LocalDateTime.now());
        entry.setPostedBy(user);

        return toJournalEntryResponse(journalEntryRepository.save(entry));
    }

    @Transactional
    public JournalEntryResponse reverseJournalEntry(Long id, String reason, User user) {
        JournalEntry original = journalEntryRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Journal entry not found: " + id));

        if (!"POSTED".equals(original.getStatus())) {
            throw new IllegalStateException("Only POSTED journal entries can be reversed");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reversal reason is required.");
        }

        LocalDate today = LocalDate.now();
        String revNumber = "REV-" + original.getEntryNumber();
        JournalEntry reversal = JournalEntry.builder()
                .entryNumber(revNumber)
                .entryDate(today)
                .accountingPeriod(YearMonth.from(today).toString())
                .reference("Reversal of " + original.getEntryNumber())
                .description("Reversal: " + (reason != null ? reason : original.getDescription()))
                .sourceType("REVERSAL")
                .sourceId(String.valueOf(original.getId()))
                .status("POSTED")
                .totalDebit(original.getTotalCredit())
                .totalCredit(original.getTotalDebit())
                .postedAt(LocalDateTime.now())
                .postedBy(user)
                .build();

        int lineNum = 1;
        for (JournalLine line : original.getLines()) {
            JournalLine revLine = JournalLine.builder()
                    .account(line.getAccount())
                    .debit(line.getCredit())
                    .credit(line.getDebit())
                    .description("Reversal line: " + line.getDescription())
                    .lineNumber(lineNum++)
                    .build();
            reversal.addLine(revLine);
        }

        JournalEntry savedReversal = journalEntryRepository.save(reversal);

        original.setStatus("REVERSED");
        original.setReversedAt(LocalDateTime.now());
        original.setReversedBy(user);
        original.setReversalEntryId(savedReversal.getId());
        original.setReversalReason(reason);
        journalEntryRepository.save(original);

        return toJournalEntryResponse(savedReversal);
    }

    @Transactional(readOnly = true)
    public List<JournalEntryResponse> listJournalEntries(String status, String period, LocalDate from, LocalDate to) {
        List<JournalEntry> entries;
        if (from != null && to != null) {
            entries = journalEntryRepository.findByEntryDateBetweenOrderByEntryDateDescIdDesc(from, to);
        } else if (period != null && !period.isBlank()) {
            entries = journalEntryRepository.findByAccountingPeriodOrderByEntryDateDescIdDesc(period);
        } else if (status != null && !status.isBlank()) {
            entries = journalEntryRepository.findByStatusOrderByEntryDateDescIdDesc(status);
        } else {
            entries = journalEntryRepository.findAllByOrderByEntryDateDescIdDesc();
        }
        return entries.stream().map(this::toJournalEntryResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse getJournalEntry(Long id) {
        return journalEntryRepository.findById(id)
                .map(this::toJournalEntryResponse)
                .orElseThrow(() -> new NoSuchElementException("Journal entry not found: " + id));
    }

    private JournalEntryResponse toJournalEntryResponse(JournalEntry e) {
        List<JournalLineResponse> lines = e.getLines().stream().map(l -> new JournalLineResponse(
                l.getId(),
                l.getAccount().getId(),
                l.getAccount().getCode(),
                l.getAccount().getName(),
                l.getDebit(),
                l.getCredit(),
                l.getDescription(),
                l.getLineNumber()
        )).collect(Collectors.toList());

        return new JournalEntryResponse(
                e.getId(),
                e.getEntryNumber(),
                e.getEntryDate(),
                e.getAccountingPeriod(),
                e.getReference(),
                e.getDescription(),
                e.getSourceType(),
                e.getSourceId(),
                e.getStatus(),
                e.getTotalDebit(),
                e.getTotalCredit(),
                e.getPostedAt(),
                e.getPostedBy() != null ? e.getPostedBy().getFullName() : null,
                e.getReversedAt(),
                e.getReversedBy() != null ? e.getReversedBy().getFullName() : null,
                e.getReversalEntryId(),
                e.getReversalReason(),
                lines
        );
    }

    private Account resolveAccount(Long id, String code) {
        if (id != null) {
            return accountRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Account not found with ID: " + id));
        }
        if (code != null && !code.isBlank()) {
            return accountRepository.findByCode(code.trim())
                    .orElseThrow(() -> new NoSuchElementException("Account not found with code: " + code));
        }
        throw new IllegalArgumentException("Either accountId or accountCode must be provided");
    }

    private String generateEntryNumber(LocalDate date) {
        return String.format("JE-%d%02d-%05d", date.getYear(), date.getMonthValue(), ENTRY_SEQ.incrementAndGet());
    }

    // ─── Automatic Operational Transaction Sync (Idempotent) ─────────────────────

    @Transactional
    public int syncOperationalTransactions(User systemUser) {
        seedStandardAccountsIfEmpty();

        Account cashAccount = accountRepository.findByCode("1010").orElse(null);
        Account arAccount = accountRepository.findByCode("1100").orElse(null);
        Account invAccount = accountRepository.findByCode("1200").orElse(null);
        Account apAccount = accountRepository.findByCode("2010").orElse(null);
        Account vatAccount = accountRepository.findByCode("2020").orElse(null);
        Account salesAccount = accountRepository.findByCode("4010").orElse(null);
        Account cogsAccount = accountRepository.findByCode("5010").orElse(null);
        Account expAccount = accountRepository.findByCode("6010").orElse(null);
        Account payrollAccount = accountRepository.findByCode("6020").orElse(null);
        Account taxPayableAccount = accountRepository.findByCode("2030").orElse(null);
        Account pensionPayableAccount = accountRepository.findByCode("2040").orElse(null);

        int synced = 0;

        // Refuse an automatic sync when an earlier operational entry is still a
        // draft. Creating a replacement would duplicate its source reference.
        if (journalEntryRepository.findAll().stream()
                .anyMatch(j -> "DRAFT".equalsIgnoreCase(j.getStatus()) && j.getSourceType() != null
                        && !"MANUAL".equalsIgnoreCase(j.getSourceType()))) {
            throw new IllegalStateException("Operational sync requires review of existing source-linked draft journals first.");
        }

        // 1. Sync Sales Orders
        List<SaleOrder> sales = saleOrderRepository.findAll();
        for (SaleOrder so : sales) {
            String sourceId = String.valueOf(so.getId());
            if (journalEntryRepository.existsBySourceTypeAndSourceId("SALE", sourceId)) {
                continue;
            }
            if ("CANCELLED".equalsIgnoreCase(so.getStatus()) || "REFUNDED".equalsIgnoreCase(so.getStatus())) {
                continue;
            }

            LocalDate entryDate = so.getTransactionDate() != null ? so.getTransactionDate().toLocalDate() : LocalDate.now();
            BigDecimal totalAmount = so.getFinalAmount() != null ? so.getFinalAmount() : (so.getTotalAmount() != null ? so.getTotalAmount() : ZERO);
            if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) continue;

            if (cashAccount == null || arAccount == null || salesAccount == null
                    || (so.getTax() != null && so.getTax().signum() > 0 && vatAccount == null)
                    || (so.getCostOfGoodsSold() != null && so.getCostOfGoodsSold().signum() > 0
                        && (cogsAccount == null || invAccount == null))) {
                throw new IllegalStateException("Required chart-of-accounts mappings are incomplete for sale " + so.getOrderNumber());
            }

            BigDecimal taxAmount = so.getTax() != null ? so.getTax() : ZERO;
            BigDecimal netSales = totalAmount.subtract(taxAmount);
            BigDecimal paid = so.getAmountPaid() != null ? so.getAmountPaid() : ("PAID".equalsIgnoreCase(so.getPaymentStatus()) || "PAID".equalsIgnoreCase(so.getStatus()) ? totalAmount : ZERO);
            BigDecimal unpaid = totalAmount.subtract(paid);
            BigDecimal cogs = so.getCostOfGoodsSold() != null ? so.getCostOfGoodsSold() : ZERO;

            JournalEntry entry = JournalEntry.builder()
                    .entryNumber("JE-SALE-" + so.getId())
                    .entryDate(entryDate)
                    .accountingPeriod(YearMonth.from(entryDate).toString())
                    .reference(so.getOrderNumber() != null ? so.getOrderNumber() : "SO-" + so.getId())
                    .description("Sale order revenue recognition: " + (so.getCustomer() != null ? so.getCustomer().getName() : "Walk-in"))
                    .sourceType("SALE")
                    .sourceId(sourceId)
                    .status("POSTED")
                    .postedAt(LocalDateTime.now())
                    .postedBy(systemUser)
                    .build();

            int lNum = 1;
            if (paid.compareTo(BigDecimal.ZERO) > 0 && cashAccount != null) {
                entry.addLine(JournalLine.builder().account(cashAccount).debit(paid).credit(ZERO).description("Cash received for sale").lineNumber(lNum++).build());
            }
            if (unpaid.compareTo(BigDecimal.ZERO) > 0 && arAccount != null) {
                entry.addLine(JournalLine.builder().account(arAccount).debit(unpaid).credit(ZERO).description("Customer receivable").lineNumber(lNum++).build());
            }

            if (netSales.compareTo(BigDecimal.ZERO) > 0 && salesAccount != null) {
                entry.addLine(JournalLine.builder().account(salesAccount).debit(ZERO).credit(netSales).description("Merchandise sales revenue").lineNumber(lNum++).build());
            }
            if (taxAmount.compareTo(BigDecimal.ZERO) > 0 && vatAccount != null) {
                entry.addLine(JournalLine.builder().account(vatAccount).debit(ZERO).credit(taxAmount).description("15% Output VAT payable").lineNumber(lNum++).build());
            }

            if (cogs.compareTo(BigDecimal.ZERO) > 0 && cogsAccount != null && invAccount != null) {
                entry.addLine(JournalLine.builder().account(cogsAccount).debit(cogs).credit(ZERO).description("Cost of goods sold").lineNumber(lNum++).build());
                entry.addLine(JournalLine.builder().account(invAccount).debit(ZERO).credit(cogs).description("Inventory reduction").lineNumber(lNum++).build());
            }

            BigDecimal totalD = entry.getLines().stream().map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalC = entry.getLines().stream().map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
            entry.setTotalDebit(totalD);
            entry.setTotalCredit(totalC);

            if (totalD.compareTo(totalC) == 0 && totalD.compareTo(BigDecimal.ZERO) > 0) {
                journalEntryRepository.save(entry);
                synced++;
            } else if (!entry.getLines().isEmpty()) {
                throw new IllegalStateException("Sale journal for " + so.getOrderNumber()
                        + " is incomplete or unbalanced; source left unchanged for review.");
            }
        }

        // 2. Sync Expenses
        List<Expense> expenses = expenseRepository.findAll();
        for (Expense exp : expenses) {
            String sourceId = String.valueOf(exp.getId());
            if (journalEntryRepository.existsBySourceTypeAndSourceId("EXPENSE", sourceId)) {
                continue;
            }
            if (!Expense.STATUS_APPROVED.equals(exp.getStatus()) || exp.getAmount() == null
                    || exp.getAmount().compareTo(BigDecimal.ZERO) <= 0) continue;

            LocalDate entryDate = exp.getTransactionDate() != null ? exp.getTransactionDate().toLocalDate() : LocalDate.now();

            JournalEntry entry = JournalEntry.builder()
                    .entryNumber("JE-EXP-" + exp.getId())
                    .entryDate(entryDate)
                    .accountingPeriod(YearMonth.from(entryDate).toString())
                    .reference("EXP-" + exp.getId())
                    .description("Operating expense: " + (exp.getDescription() != null ? exp.getDescription() : exp.getCategory()))
                    .sourceType("EXPENSE")
                    .sourceId(sourceId)
                    .status("POSTED")
                    .postedAt(LocalDateTime.now())
                    .postedBy(systemUser)
                    .totalDebit(exp.getAmount())
                    .totalCredit(exp.getAmount())
                    .build();

            if (expAccount != null && cashAccount != null) {
                entry.addLine(JournalLine.builder().account(expAccount).debit(exp.getAmount()).credit(ZERO).description(exp.getCategory()).lineNumber(1).build());
                entry.addLine(JournalLine.builder().account(cashAccount).debit(ZERO).credit(exp.getAmount()).description("Payment for expense").lineNumber(2).build());
                journalEntryRepository.save(entry);
                synced++;
            }
        }

        // 3. Sync Finalized Payroll Periods
        List<PayrollPeriod> payrollPeriods = payrollPeriodRepository.findAll();
        for (PayrollPeriod p : payrollPeriods) {
            String sourceId = String.valueOf(p.getId());
            if (journalEntryRepository.existsBySourceTypeAndSourceId("PAYROLL", sourceId)) {
                continue;
            }
            if ("DRAFT".equalsIgnoreCase(p.getStatus())) continue;

            BigDecimal gross = p.getGrossPayroll() != null ? p.getGrossPayroll() : ZERO;
            if (gross.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal empTax = p.getEmploymentTax() != null ? p.getEmploymentTax() : ZERO;
            BigDecimal empPension = p.getEmployeePension() != null ? p.getEmployeePension() : ZERO;
            BigDecimal employerPension = p.getEmployerPension() != null ? p.getEmployerPension() : ZERO;
            BigDecimal otherDeductions = payrollItemRepository.findByPayrollPeriodId(p.getId()).stream()
                    .map(PayrollItem::getOtherDeductions).filter(Objects::nonNull)
                    .reduce(ZERO, BigDecimal::add);
            BigDecimal totalPension = empPension.add(employerPension);
            BigDecimal netPay = p.getNetPayroll() != null ? p.getNetPayroll()
                    : gross.subtract(empTax).subtract(empPension).subtract(otherDeductions);
            BigDecimal totalEmployerExpense = gross.add(employerPension);

            LocalDate entryDate = LocalDate.of(p.getYear(), p.getMonth(), 1);

            JournalEntry entry = JournalEntry.builder()
                    .entryNumber(String.format("JE-PAY-%04d%02d", p.getYear(), p.getMonth()))
                    .entryDate(entryDate)
                    .accountingPeriod(String.format("%04d-%02d", p.getYear(), p.getMonth()))
                    .reference(String.format("PAY-%04d-%02d", p.getYear(), p.getMonth()))
                    .description(String.format("Payroll expense and statutory deductions for %04d-%02d", p.getYear(), p.getMonth()))
                    .sourceType("PAYROLL")
                    .sourceId(sourceId)
                    .status("POSTED")
                    .postedAt(LocalDateTime.now())
                    .postedBy(systemUser)
                    .totalDebit(totalEmployerExpense)
                    .totalCredit(totalEmployerExpense)
                    .build();

            int lNum = 1;
            if (payrollAccount != null) {
                entry.addLine(JournalLine.builder().account(payrollAccount).debit(totalEmployerExpense).credit(ZERO).description("Gross wages + employer pension").lineNumber(lNum++).build());
            }
            if (taxPayableAccount != null && empTax.compareTo(BigDecimal.ZERO) > 0) {
                entry.addLine(JournalLine.builder().account(taxPayableAccount).debit(ZERO).credit(empTax).description("Withheld salary tax payable to ERCA").lineNumber(lNum++).build());
            }
            if (pensionPayableAccount != null && totalPension.compareTo(BigDecimal.ZERO) > 0) {
                entry.addLine(JournalLine.builder().account(pensionPayableAccount).debit(ZERO).credit(totalPension).description("POESSA pension contributions payable").lineNumber(lNum++).build());
            }
            if (otherDeductions.signum() > 0 && taxPayableAccount != null) {
                entry.addLine(JournalLine.builder().account(taxPayableAccount).debit(ZERO).credit(otherDeductions)
                        .description("Other payroll deductions payable (classification required)").lineNumber(lNum++).build());
            }
            if (cashAccount != null && netPay.compareTo(BigDecimal.ZERO) > 0) {
                entry.addLine(JournalLine.builder().account(cashAccount).debit(ZERO).credit(netPay).description("Net salary disbursement").lineNumber(lNum++).build());
            }

            BigDecimal totalD = entry.getLines().stream().map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalC = entry.getLines().stream().map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalD.compareTo(totalC) == 0 && totalD.compareTo(BigDecimal.ZERO) > 0) {
                journalEntryRepository.save(entry);
                synced++;
            }
        }

        log.info("Synchronized {} operational transactions into double-entry journal", synced);
        return synced;
    }

    // ─── Financial Statements & Reports ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public GeneralLedgerReport getGeneralLedger(Long accountId, LocalDate from, LocalDate to) {
        seedStandardAccountsIfEmpty();

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NoSuchElementException("Account not found with ID: " + accountId));

        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now();

        BigDecimal priorNet = journalLineRepository.computeNetBalanceBefore(accountId, start);
        if (priorNet == null) priorNet = BigDecimal.ZERO;
        if (!"ASSET".equalsIgnoreCase(account.getCategory()) && !"EXPENSE".equalsIgnoreCase(account.getCategory())) {
            priorNet = priorNet.negate();
        }

        List<JournalLine> lines = journalLineRepository.findPostedLinesForAccountBetween(accountId, start, end);

        BigDecimal runningBalance = priorNet;
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        List<LedgerTransaction> txs = new ArrayList<>();

        boolean isNormalDebit = "ASSET".equalsIgnoreCase(account.getCategory()) || "EXPENSE".equalsIgnoreCase(account.getCategory());

        for (JournalLine line : lines) {
            BigDecimal d = line.getDebit();
            BigDecimal c = line.getCredit();
            totalDebit = totalDebit.add(d);
            totalCredit = totalCredit.add(c);

            if (isNormalDebit) {
                runningBalance = runningBalance.add(d).subtract(c);
            } else {
                runningBalance = runningBalance.add(c).subtract(d);
            }

            JournalEntry parent = line.getJournalEntry();
            txs.add(new LedgerTransaction(
                    parent.getEntryDate(),
                    parent.getId(),
                    parent.getEntryNumber(),
                    parent.getReference(),
                    line.getDescription() != null ? line.getDescription() : parent.getDescription(),
                    d, c, runningBalance
            ));
        }

        return new GeneralLedgerReport(
                toAccountResponse(account),
                start, end,
                priorNet.setScale(2, RoundingMode.HALF_UP),
                txs,
                totalDebit.setScale(2, RoundingMode.HALF_UP),
                totalCredit.setScale(2, RoundingMode.HALF_UP),
                runningBalance.setScale(2, RoundingMode.HALF_UP)
        );
    }

    @Transactional(readOnly = true)
    public TrialBalanceReport getTrialBalance(LocalDate asOfDate) {
        seedStandardAccountsIfEmpty();
        LocalDate cutoff = asOfDate != null ? asOfDate : LocalDate.now();

        List<Account> accounts = accountRepository.findAllByOrderByCodeAsc();
        List<TrialBalanceItem> items = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (Account a : accounts) {
            BigDecimal net = journalLineRepository.computeNetBalanceBefore(a.getId(), cutoff.plusDays(1));
            if (net == null) net = BigDecimal.ZERO;

            BigDecimal dBalance = ZERO;
            BigDecimal cBalance = ZERO;

            if (net.compareTo(BigDecimal.ZERO) > 0) {
                dBalance = net.setScale(2, RoundingMode.HALF_UP);
                totalDebit = totalDebit.add(dBalance);
            } else if (net.compareTo(BigDecimal.ZERO) < 0) {
                cBalance = net.negate().setScale(2, RoundingMode.HALF_UP);
                totalCredit = totalCredit.add(cBalance);
            }

            items.add(new TrialBalanceItem(
                    a.getCode(), a.getName(), a.getCategory(), dBalance, cBalance
            ));
        }

        boolean balanced = totalDebit.compareTo(totalCredit) == 0;
        BigDecimal discrepancy = totalDebit.subtract(totalCredit).abs();

        return new TrialBalanceReport(cutoff, items, totalDebit, totalCredit, balanced, discrepancy);
    }

    @Transactional(readOnly = true)
    public ProfitLossReport getProfitLoss(LocalDate from, LocalDate to) {
        seedStandardAccountsIfEmpty();

        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);

        FinancialSummary summary = financialCalculationService.summarise(start, end);

        BigDecimal revenue = summary.grossSales() != null ? summary.grossSales() : ZERO;
        BigDecimal returns = summary.salesReturns() != null ? summary.salesReturns() : ZERO;
        BigDecimal netSales = summary.netSales() != null ? summary.netSales() : revenue.subtract(returns);
        BigDecimal cogs = summary.costOfGoodsSold() != null ? summary.costOfGoodsSold() : ZERO;
        BigDecimal grossProfit = summary.grossProfit() != null ? summary.grossProfit() : netSales.subtract(cogs);
        BigDecimal marginPercent = summary.grossMarginPercent() != null ? summary.grossMarginPercent() : ZERO;

        BigDecimal operatingExpenses = summary.operatingExpenses() != null ? summary.operatingExpenses() : ZERO;

        PayrollDTO.Dashboard payroll = payrollService.dashboard();
        BigDecimal payrollExpense = payroll.hasData() && payroll.grossPayroll() != null ? payroll.grossPayroll() : ZERO;

        List<FinancialStatementItem> expenseItems = List.of(
                new FinancialStatementItem("6010", "Operating Expenses", "OPERATING_EXPENSE", operatingExpenses),
                new FinancialStatementItem("6020", "Salaries & Payroll", "OPERATING_EXPENSE", payrollExpense)
        );

        BigDecimal totalExpenses = operatingExpenses.add(payrollExpense);
        BigDecimal netProfit = grossProfit.subtract(totalExpenses);

        return new ProfitLossReport(
                start, end,
                revenue, returns, netSales, cogs,
                grossProfit, marginPercent,
                expenseItems,
                operatingExpenses,
                payrollExpense,
                netProfit,
                "Accrual accounting basis derived from persisted sale orders, receipts, expenses, and payroll runs"
        );
    }

    @Transactional(readOnly = true)
    public BalanceSheetReport getBalanceSheet(LocalDate asOfDate) {
        seedStandardAccountsIfEmpty();

        LocalDate cutoff = asOfDate != null ? asOfDate : LocalDate.now();
        FinancialSummary summary = financialCalculationService.summarise(cutoff.minusDays(365), cutoff);

        BigDecimal cash = computeAccountBalance("1010", "ASSET");
        BigDecimal bank = computeAccountBalance("1020", "ASSET");
        BigDecimal ar = computeAccountBalance("1100", "ASSET");
        BigDecimal inventoryVal = summary.inventoryValue() != null ? summary.inventoryValue() : ZERO;

        List<FinancialStatementItem> currentAssets = List.of(
                new FinancialStatementItem("1010", "Cash on Hand", "CURRENT_ASSET", cash),
                new FinancialStatementItem("1020", "Bank & Mobile Money", "CURRENT_ASSET", bank),
                new FinancialStatementItem("1100", "Accounts Receivable", "CURRENT_ASSET", ar),
                new FinancialStatementItem("1200", "Merchandise Inventory", "CURRENT_ASSET", inventoryVal)
        );
        BigDecimal totalCurrentAssets = cash.add(bank).add(ar).add(inventoryVal);
        BigDecimal totalAssets = totalCurrentAssets;

        BigDecimal ap = computeAccountBalance("2010", "LIABILITY");
        TaxSummaryDTO tax = taxService.summary(YearMonth.from(cutoff));
        BigDecimal vatLiability = tax.estimatedNetVat() != null && tax.estimatedNetVat().compareTo(BigDecimal.ZERO) > 0 ? tax.estimatedNetVat() : ZERO;

        PayrollDTO.Dashboard payroll = payrollService.dashboard();
        BigDecimal payrollLiability = payroll.hasData() && payroll.totalPayrollOutstanding() != null ? payroll.totalPayrollOutstanding() : ZERO;

        List<FinancialStatementItem> currentLiabilities = List.of(
                new FinancialStatementItem("2010", "Accounts Payable (Suppliers)", "CURRENT_LIABILITY", ap),
                new FinancialStatementItem("2020", "Net VAT Payable (ERCA)", "CURRENT_LIABILITY", vatLiability),
                new FinancialStatementItem("2030", "Payroll & Pension Liabilities", "CURRENT_LIABILITY", payrollLiability)
        );
        BigDecimal totalLiabilities = ap.add(vatLiability).add(payrollLiability);

        BigDecimal retainedEarnings = totalAssets.subtract(totalLiabilities);
        List<FinancialStatementItem> equityItems = List.of(
                new FinancialStatementItem("3010", "Owner's Equity & Retained Earnings", "EQUITY", retainedEarnings)
        );
        BigDecimal totalEquity = retainedEarnings;
        BigDecimal totalLiabilitiesAndEquity = totalLiabilities.add(totalEquity);

        boolean balanced = totalAssets.compareTo(totalLiabilitiesAndEquity) == 0;
        BigDecimal discrepancy = totalAssets.subtract(totalLiabilitiesAndEquity).abs();

        return new BalanceSheetReport(
                cutoff,
                currentAssets,
                List.of(),
                totalAssets,
                currentLiabilities,
                List.of(),
                totalLiabilities,
                equityItems,
                retainedEarnings,
                totalEquity,
                totalLiabilitiesAndEquity,
                balanced,
                discrepancy,
                "Standard balance sheet derived from live inventory valuation, receivables, payables, and tax/payroll liabilities"
        );
    }

    private BigDecimal computeAccountBalance(String code, String category) {
        return accountRepository.findByCode(code)
                .map(a -> computeAccountBalance(a.getId(), category))
                .orElse(ZERO);
    }

    @Transactional(readOnly = true)
    public ReconciliationReport getReconciliation(LocalDate asOfDate) {
        LocalDate cutoff = asOfDate != null ? asOfDate : LocalDate.now();
        List<ReconciliationItem> items = new ArrayList<>();

        // 1. Tax reconciliation: Output VAT vs Payments
        TaxSummaryDTO tax = taxService.summary(YearMonth.from(cutoff));
        BigDecimal netVat = tax.estimatedNetVat() != null ? tax.estimatedNetVat() : ZERO;
        BigDecimal taxPaid = tax.taxPaymentsMade() != null ? tax.taxPaymentsMade() : ZERO;
        BigDecimal taxVariance = netVat.subtract(taxPaid);
        items.add(new ReconciliationItem(
                "TAX",
                "VAT-" + YearMonth.from(cutoff),
                "VAT Liability vs Payments to ERCA",
                netVat, taxPaid, taxVariance,
                taxVariance.compareTo(BigDecimal.ZERO) == 0 ? "RECONCILED" : "DISCREPANCY",
                taxVariance.compareTo(BigDecimal.ZERO) > 0 ? "Outstanding VAT liability requires payment before filing deadline" : "Fully settled or in credit"
        ));

        // 2. Payroll reconciliation: Gross + Pension vs Paid
        PayrollDTO.Dashboard payroll = payrollService.dashboard();
        if (payroll.hasData()) {
            BigDecimal payrollDue = payroll.employerCost() != null ? payroll.employerCost() : ZERO;
            BigDecimal payrollPaid = payroll.totalPayrollPaid() != null ? payroll.totalPayrollPaid() : ZERO;
            BigDecimal payrollVariance = payrollDue.subtract(payrollPaid);
            items.add(new ReconciliationItem(
                    "PAYROLL",
                    "PAYROLL-" + YearMonth.from(cutoff),
                    "Total Employer Payroll Cost vs Disbursed",
                    payrollDue, payrollPaid, payrollVariance,
                    payrollVariance.compareTo(BigDecimal.ZERO) == 0 ? "RECONCILED" : "PENDING_DISBURSEMENT",
                    payrollVariance.compareTo(BigDecimal.ZERO) > 0 ? "Unpaid salary or pension contributions detected" : "All finalized payroll disbursed"
            ));
        }

        // 3. Sales Credit vs Receivables
        BigDecimal totalCreditSales = saleOrderRepository.findAll().stream()
                .filter(s -> "UNPAID".equalsIgnoreCase(s.getPaymentStatus()) || "PARTIAL".equalsIgnoreCase(s.getPaymentStatus()))
                .map(s -> s.getFinalAmount() != null ? s.getFinalAmount().subtract(s.getAmountPaid() != null ? s.getAmountPaid() : ZERO) : ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal recordedAr = computeAccountBalance("1100", "ASSET");
        BigDecimal arVariance = totalCreditSales.subtract(recordedAr);
        items.add(new ReconciliationItem(
                "RECEIVABLE",
                "AR-RECON",
                "Customer Order Receivables vs Ledger Balance",
                totalCreditSales, recordedAr, arVariance,
                arVariance.compareTo(BigDecimal.ZERO) == 0 ? "RECONCILED" : "INVESTIGATE",
                arVariance.compareTo(BigDecimal.ZERO) != 0 ? "Discrepancy between order credit balances and AR ledger" : "Ledger matches sales ledger"
        ));

        int discrepancies = (int) items.stream().filter(i -> !"RECONCILED".equals(i.status())).count();
        BigDecimal totalDiscrepancy = items.stream().map(i -> i.variance().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ReconciliationReport(
                cutoff,
                items,
                items.size(),
                discrepancies,
                totalDiscrepancy,
                discrepancies == 0 ? "ALL_RECONCILED" : "ACTION_REQUIRED"
        );
    }

    @Transactional(readOnly = true)
    public AiAccountingInsight getAiAccountingInsights(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);

        FinancialSummary summary = financialCalculationService.summarise(start, end);
        TaxSummaryDTO tax = taxService.summary(YearMonth.from(end));
        PayrollDTO.Dashboard payroll = payrollService.dashboard();

        List<String> keyTrends = new ArrayList<>();
        List<String> anomalies = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        if (summary.netSales() != null && summary.netSales().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal margin = summary.grossMarginPercent() != null ? summary.grossMarginPercent() : ZERO;
            keyTrends.add(String.format("Net sales reached ETB %s with a gross profit margin of %s%%.", summary.netSales(), margin));
            if (margin.compareTo(new BigDecimal("15")) < 0) {
                anomalies.add("Gross profit margin is below the healthy 15% threshold; review discounting and purchase prices.");
            }
        } else {
            keyTrends.add("No sales recorded in the active reporting window.");
        }

        if (tax.estimatedNetVat() != null && tax.estimatedNetVat().compareTo(BigDecimal.ZERO) > 0) {
            keyTrends.add(String.format("Net VAT liability stands at ETB %s (Output: ETB %s, Recoverable Input: ETB %s).",
                    tax.estimatedNetVat(), tax.outputVat(), tax.eligibleInputVat()));
            if ((tax.outstandingLiabilities() != null && tax.outstandingLiabilities().compareTo(BigDecimal.ZERO) > 0)) {
                recommendations.add(String.format("Settle outstanding VAT liability of ETB %s with ERCA before month-end penalty window.", tax.outstandingLiabilities()));
            }
        }

        if (payroll.hasData() && payroll.draftPeriods() > 0) {
            anomalies.add(String.format("%d payroll period(s) remain in DRAFT status and have not been finalized.", payroll.draftPeriods()));
            recommendations.add("Review and finalize draft payroll periods to calculate accurate statutory tax and pension liabilities.");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("All primary accounts and statutory obligations are in regular order.");
        }

        return new AiAccountingInsight(
                "Accounting & Compliance Intelligence Summary",
                String.format("Comprehensive financial review for period %s to %s based on %s posted records.",
                        start, end, journalEntryRepository.countPosted()),
                keyTrends, anomalies, recommendations,
                start + " to " + end,
                LocalDateTime.now().toString()
        );
    }
}
