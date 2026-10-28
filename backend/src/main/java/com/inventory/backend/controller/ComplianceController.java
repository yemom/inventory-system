package com.inventory.backend.controller;

import com.inventory.backend.compliance.*;
import com.inventory.backend.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceService complianceService;
    private final ComplianceAiService complianceAiService;

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('COMPLIANCE_VIEW')")
    public ResponseEntity<ApiResponse<ComplianceSummaryDTO>> summary(
            @RequestParam(required = false) String period) {
        YearMonth month = (period == null || period.isBlank())
                ? YearMonth.now() : YearMonth.parse(period);
        return ResponseEntity.ok(ApiResponse.ok(
                "Compliance summary and findings retrieved",
                complianceService.getSummary(month)));
    }

    @PostMapping("/ai-analyze")
    @PreAuthorize("hasAuthority('AI_COMPLIANCE_ANALYZE')")
    public ResponseEntity<ApiResponse<ComplianceAiDTO.AnalysisResponse>> analyze(
            @RequestParam(required = false) String period) {
        YearMonth month = (period == null || period.isBlank())
                ? YearMonth.now() : YearMonth.parse(period);
        return ResponseEntity.ok(ApiResponse.ok(
                "AI compliance analysis generated",
                complianceAiService.analyze(month)));
    }

    @PostMapping("/ask")
    @PreAuthorize("hasAuthority('AI_COMPLIANCE_ANALYZE')")
    public ResponseEntity<ApiResponse<ComplianceAiDTO.QuestionResponse>> ask(
            @RequestBody ComplianceAiDTO.QuestionRequest request) {
        YearMonth month = (request.period() == null || request.period().isBlank())
                ? YearMonth.now() : YearMonth.parse(request.period());
        return ResponseEntity.ok(ApiResponse.ok(
                "Compliance question answered",
                complianceAiService.askQuestion(request.question(), month)));
    }
}
