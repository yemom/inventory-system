package com.inventory.backend.controller;

import com.inventory.backend.dto.*;
import com.inventory.backend.service.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('PAYROLL_SUMMARY_VIEW')")
    public ResponseEntity<ApiResponse<PayrollDTO.Dashboard>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Payroll dashboard retrieved", payrollService.dashboard()));
    }

    @GetMapping("/profiles")
    @PreAuthorize("hasAuthority('PAYROLL_DETAIL_VIEW')")
    public ResponseEntity<ApiResponse<List<PayrollDTO.Profile>>> profiles() {
        return ResponseEntity.ok(ApiResponse.ok("Payroll profiles retrieved", payrollService.profiles()));
    }

    @PostMapping("/profiles")
    @PreAuthorize("hasAuthority('PAYROLL_PROCESS')")
    public ResponseEntity<ApiResponse<PayrollDTO.Profile>> profile(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(String.valueOf(body.get("userId")));
        BigDecimal salary = new BigDecimal(String.valueOf(body.get("basicSalary")));
        LocalDate effective = LocalDate.parse(String.valueOf(body.get("effectiveFrom")));
        boolean pension = !Boolean.FALSE.equals(body.get("pensionEligible"));
        String maskedBank = (String) body.get("bankAccountMasked");
        return ResponseEntity.ok(ApiResponse.ok("Payroll profile saved",
                payrollService.saveProfile(userId, salary, effective, pension, maskedBank)));
    }

    @GetMapping("/periods")
    @PreAuthorize("hasAuthority('PAYROLL_SUMMARY_VIEW')")
    public ResponseEntity<ApiResponse<List<PayrollDTO.Period>>> periods() {
        return ResponseEntity.ok(ApiResponse.ok("Payroll periods retrieved", payrollService.periods()));
    }

    @GetMapping("/periods/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_DETAIL_VIEW')")
    public ResponseEntity<ApiResponse<PayrollDTO.Period>> period(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Payroll period retrieved", payrollService.period(id)));
    }

    @PostMapping("/periods")
    @PreAuthorize("hasAuthority('PAYROLL_PROCESS')")
    public ResponseEntity<ApiResponse<PayrollDTO.Period>> create(@RequestBody Map<String, Integer> body) {
        return ResponseEntity.ok(ApiResponse.ok("Draft payroll created",
                payrollService.createPeriod(body.get("year"), body.get("month"))));
    }

    @PostMapping("/periods/{id}/finalize")
    @PreAuthorize("hasAuthority('PAYROLL_FINALIZE')")
    public ResponseEntity<ApiResponse<PayrollDTO.Period>> finalizePeriod(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Payroll finalized", payrollService.finalizePeriod(id)));
    }

    @PostMapping("/periods/{id}/pay")
    @PreAuthorize("hasAuthority('PAYROLL_FINALIZE')")
    public ResponseEntity<ApiResponse<PayrollDTO.Period>> payPeriod(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Payroll marked as paid and payment recorded",
                payrollService.payPeriod(id)));
    }

    @GetMapping("/items/{itemId}/payslip")
    @PreAuthorize("hasAuthority('PAYSLIP_VIEW')")
    public ResponseEntity<ApiResponse<PayrollDTO.Item>> payslip(@PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.ok("Employee payslip retrieved", payrollService.payslip(itemId)));
    }
}
