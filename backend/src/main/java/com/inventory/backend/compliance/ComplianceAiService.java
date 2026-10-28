package com.inventory.backend.compliance;

import com.inventory.backend.chatbot.ChatbotProvider;
import com.inventory.backend.chatbot.OpenAiCompatibleProvider;
import com.inventory.backend.dto.TaxSummaryDTO;
import com.inventory.backend.service.TaxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceAiService {

    private final ComplianceService complianceService;
    private final TaxService taxService;
    private final OpenAiCompatibleProvider openAiProvider;
    private final ChatbotProvider deterministicProvider;

    public ComplianceAiDTO.AnalysisResponse analyze(YearMonth targetMonth) {
        YearMonth month = targetMonth != null ? targetMonth : YearMonth.now();
        ComplianceSummaryDTO summary = complianceService.getSummary(month);
        TaxSummaryDTO taxSummary = taxService.summary(month);

        BigDecimal totalExposure = (summary.outstandingTaxLiability() != null ? summary.outstandingTaxLiability() : BigDecimal.ZERO)
                .add(summary.unpaidPayrollLiability() != null ? summary.unpaidPayrollLiability() : BigDecimal.ZERO);

        List<ComplianceAiDTO.AiFindingDetail> aiFindings = summary.findings().stream()
                .map(f -> new ComplianceAiDTO.AiFindingDetail(
                        f.id(),
                        f.title(),
                        f.severity(),
                        f.category(),
                        f.explanation(),
                        f.sourceRecords(),
                        f.amount() != null ? f.amount().toPlainString() + " ETB" : "N/A",
                        f.recommendedAction(),
                        f.humanApprovalRequired()
                ))
                .toList();

        List<String> prioritizedActions = new ArrayList<>();
        if (summary.outstandingTaxLiability().signum() > 0) {
            prioritizedActions.add("Prioritize remittance of " + summary.outstandingTaxLiability().toPlainString() + " ETB Net VAT liability before statutory deadline (" + summary.nextDeadline() + ").");
        }
        if (summary.unpaidPayrollLiability().signum() > 0) {
            prioritizedActions.add("Disburse approved payroll (" + summary.unpaidPayrollLiability().toPlainString() + " ETB) and mark period as PAID.");
        }
        prioritizedActions.add("Review active tax rules with your certified accountant and mark verified rules as VERIFIED.");
        prioritizedActions.add("Audit high-value sales with zero tax to verify withholding or exemption certificates.");

        String riskLevel = summary.overallStatus().equals("COMPLIANT") ? "LOW"
                : (summary.overallStatus().equals("ACTION_REQUIRED") ? "MEDIUM" : "HIGH");

        String executiveSummary = generateExecutiveSummary(summary, taxSummary, totalExposure, month);

        return new ComplianceAiDTO.AnalysisResponse(
                "StockFlow Ethiopian Statutory Compliance & Financial Health Analysis",
                executiveSummary,
                riskLevel,
                summary.complianceScore(),
                totalExposure,
                aiFindings,
                prioritizedActions,
                openAiProvider != null && openAiProvider.available() ? openAiProvider.name() : "deterministic-rules-engine",
                month.toString(),
                LocalDateTime.now()
        );
    }

    public ComplianceAiDTO.QuestionResponse askQuestion(String question, YearMonth targetMonth) {
        YearMonth month = targetMonth != null ? targetMonth : YearMonth.now();
        ComplianceSummaryDTO summary = complianceService.getSummary(month);
        TaxSummaryDTO taxSummary = taxService.summary(month);

        String context = String.format("""
                Period: %s
                Overall Status: %s (Score: %d/100)
                Outstanding Net VAT: %s ETB (Output VAT: %s ETB, Eligible Input VAT: %s ETB)
                Unpaid Payroll Liability: %s ETB
                Upcoming Deadline: %s
                Active Findings Count: %d
                Tax Filing Status: %s
                Payroll Status: %s
                """,
                month, summary.overallStatus(), summary.complianceScore(),
                summary.outstandingTaxLiability(), taxSummary.outputVat(), taxSummary.eligibleInputVat(),
                summary.unpaidPayrollLiability(), summary.nextDeadline(), summary.findings().size(),
                summary.taxFilingStatus(), summary.payrollStatus());

        String answer;
        String providerUsed = "deterministic-rules-engine";

        if (openAiProvider != null && openAiProvider.available()) {
            try {
                ChatbotProvider.ChatRequest request = new ChatbotProvider.ChatRequest(
                        "You are the StockFlow AI Compliance Assistant. Answer the question factually and concisely using ONLY the provided verified facts. Never invent figures. Citing Ethiopian Proclamation No. 1341/2024 (VAT) and No. 1395/2025 (Income Tax) where relevant.",
                        question + "\n\nVerified Fact Sheet:\n" + context,
                        List.of(),
                        Map.of("facts", context)
                );
                ChatbotProvider.ChatCompletionResponse resp = openAiProvider.generate(request);
                if (resp.usable() && resp.text() != null && !resp.text().isBlank()) {
                    answer = resp.text();
                    providerUsed = openAiProvider.name();
                } else {
                    answer = deterministicAnswer(question, summary, taxSummary);
                }
            } catch (Exception e) {
                log.warn("OpenAI provider call failed for compliance ask: {}", e.getMessage());
                answer = deterministicAnswer(question, summary, taxSummary);
            }
        } else {
            answer = deterministicAnswer(question, summary, taxSummary);
        }

        List<String> sources = List.of(
                "Ethiopian VAT Proclamation No. 1341/2024",
                "Ethiopian Income Tax Proclamation No. 979/2016 & Amendment 1395/2025",
                "Private Organizations Employees' Pension Proclamation No. 1268/2022",
                "StockFlow Verified Ledger (" + month + ")"
        );

        return new ComplianceAiDTO.QuestionResponse(answer, sources, providerUsed, LocalDateTime.now());
    }

    private String generateExecutiveSummary(ComplianceSummaryDTO summary, TaxSummaryDTO taxSummary, BigDecimal totalExposure, YearMonth month) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Compliance evaluation for %s reflects an overall status of %s with a composite compliance score of %d/100. ",
                month, summary.overallStatus(), summary.complianceScore()));

        if (summary.outstandingTaxLiability().signum() > 0) {
            sb.append(String.format("There is an outstanding net VAT liability of %s ETB (Output VAT %s ETB vs Eligible Input VAT %s ETB) which must be declared and settled before %s under Ethiopian VAT Proclamation No. 1341/2024. ",
                    summary.outstandingTaxLiability(), taxSummary.outputVat(), taxSummary.eligibleInputVat(), summary.nextDeadline()));
        } else {
            sb.append("Current period VAT position is either reconciled or in an input tax credit carryforward position. ");
        }

        if (summary.unpaidPayrollLiability().signum() > 0) {
            sb.append(String.format("Undisbursed net payroll liabilities total %s ETB across finalized payroll runs. ",
                    summary.unpaidPayrollLiability()));
        }

        if (summary.findings().isEmpty()) {
            sb.append("No material statutory non-compliance or reconciliation discrepancies were detected in the verified transaction ledger.");
        } else {
            sb.append(String.format("A total of %d compliance items (%d high-priority) require human accountant and management review.",
                    summary.findings().size(), summary.highPriorityCount()));
        }

        return sb.toString();
    }

    private String deterministicAnswer(String question, ComplianceSummaryDTO summary, TaxSummaryDTO taxSummary) {
        String q = question.toLowerCase();
        if (q.contains("vat") || q.contains("tax")) {
            return String.format("For the period, Output VAT is %s ETB and Eligible Input VAT is %s ETB, resulting in a Net VAT position of %s ETB (Outstanding: %s ETB). Filing is due by %s under Proclamation No. 1341/2024 Art. 38.",
                    taxSummary.outputVat(), taxSummary.eligibleInputVat(), taxSummary.estimatedNetVat(), summary.outstandingTaxLiability(), summary.nextDeadline());
        }
        if (q.contains("payroll") || q.contains("salary") || q.contains("pension")) {
            return String.format("Payroll status is %s. Unpaid finalized payroll obligations amount to %s ETB. Progressive employment tax follows Proclamation No. 1395/2025 and pension contributions follow Proclamation No. 1268/2022 (7%% employee, 11%% employer).",
                    summary.payrollStatus(), summary.unpaidPayrollLiability());
        }
        if (q.contains("deadline") || q.contains("due")) {
            return String.format("The next statutory filing and payment deadline is %s for both monthly VAT returns (Proc. 1341/2024) and employment withholding remittances (Proc. 979/2016).",
                    summary.nextDeadline());
        }
        if (q.contains("score") || q.contains("status")) {
            return String.format("The compliance score is %d/100 (%s). There are %d findings requiring review (%d high priority).",
                    summary.complianceScore(), summary.overallStatus(), summary.findings().size(), summary.highPriorityCount());
        }
        return String.format("Based on verified records for this period: Compliance status is %s (%d/100), outstanding tax liability is %s ETB, and outstanding payroll liability is %s ETB. Next filing deadline is %s.",
                summary.overallStatus(), summary.complianceScore(), summary.outstandingTaxLiability(), summary.unpaidPayrollLiability(), summary.nextDeadline());
    }
}
