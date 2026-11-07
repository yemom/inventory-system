# Tax Rule Validation Document — StockFlow Ethiopian Compliance

**Date:** 2026-11-07
**Purpose:** Document tax rule configuration and professional review status for Ethiopian FTA/ERCA compliance claims.
**Scope:** VAT (15%), Employment Income Tax, Pension (7%/11%), Withholding Tax (2%)

---

## 1. Tax Rules Configured in System

| Code | Type | Rate | Effective From | Verification Status | Source/Notes |
|------|------|------|----------------|---------------------|--------------|
| `VAT_STANDARD` | VAT | 0.15 (15%) | 2024-01-01 | `ACCOUNTANT_REVIEW_REQUIRED` | Ethiopian standard VAT rate per Proc. No. 1341/2024 Art. 38 |
| `EMP_INCOME_TAX` | Employment Income Tax | TBD | TBD | `ACCOUNTANT_REVIEW_REQUIRED` | Per Proc. No. 979/2016 Art. 88 |
| `EMP_PENSION` | Employee Pension | TBD | TBD | `ACCOUNTANT_REVIEW_REQUIRED` | 7% (private) or 11% (public sector) |
| `EMP_PENSION_EMPLOYER` | Employer Pension | TBD | TBD | `ACCOUNTANT_REVIEW_REQUIRED` | Employer contribution matching |
| `WITHHOLDING_2pct` | Withholding Tax | 0.02 (2%) | TBD | `ACCOUNTANT_REVIEW_REQUIRED` | On applicable business purchases |

---

## 2. VAT Calculation Rules (Implemented in TaxService.java)

### 2.1 Output VAT (Sales)
- **Rate:** 15% on taxable sales
- **Calculation:** `outputVat = sum(saleTax)` where saleTax comes from SaleOrder.getTax()
- **Returns:** `salesReturnVat` is reversed from approved sales return items
- **Net Output:** `outputVat = max(0, outputVat - salesReturnVat)`

### 2.2 Input VAT (Purchases)
- **Current Status:** Purchase order VAT is treated as **UNVERIFIED** / non-recoverable
- **Reason:** No purchase classifications or supporting evidence in the system yet
- **Receipt-based Input VAT:** Standard receipts (treatment = "STANDARD") are included in `eligibleInputVat`
- **Non-recoverable:** Purchase order tax amounts and non-standard receipt treatments are excluded from eligible input

### 2.3 Net VAT Payable
```
estimatedNetVat = outputVat - eligibleInputVat
```
- If positive: VAT payable to FTA
- If negative: VAT credit (refund or offset)

### 2.4 Withholding Tax (2% on applicable purchases)
- Applied on OUT-going payments with "TAX" in reference/notes/party type
- Currently included in payment filtering but rate configurable

### 2.5 Employment Income Tax (withholding)
- Monthly withholding on employee salaries per Proc. No. 979/2016 Art. 88
- Rate currently TBD — requires professional review

### 2.6 Pension Contributions
- **Employee:** 7% (private) or 11% (public sector) of gross payroll
- **Employer:** Matching contribution rate TBD
- Currently modeled as separate `employeePension` and `employerPension` fields on PayrollPeriod

---

## 3. Professional Review Status

| Rule | Status | Reviewer | Date | Comments |
|------|--------|----------|------|----------|
| `VAT_STANDARD` (15%) | ⚠️ PENDING REVIEW | Not yet reviewed | — | Ethiopian FTA rate; must confirm with tax accountant |
| `EMP_INCOME_TAX` | ❌ NOT CONFIGURED | — | — | Proc. No. 979/2016 Art. 88 rates not yet entered |
| `EMP_PENSION` | ❌ NOT CONFIGURED | — | — | 7%/11% split not yet entered |
| `EMP_PENSION_EMPLOYER` | ❌ NOT CONFIGURED | — | — | Employer match not yet entered |
| `WITHHOLDING_2pct` | ⚠️ PENDING REVIEW | — | — | 2% on purchases; confirm applicability |

**Overall Compliance Claim:** ❌ **CANNOT CLAIM FULL ETHIOPIAN FTA COMPLIANCE** without documented tax accountant review and rule verification.

**Required External Review:**
1. Ethiopian tax accountant must review and verify all tax rule rates
2. Rules must be set to `VERIFIED` status after accountant sign-off
3. Effective dates must align with FTA regulation issuance dates
4. Pension contribution splits (7%/11%) must be confirmed for employee categories

---

## 4. Recommended Actions

### 4.1 Immediate (Before claiming compliance)
- [ ] Engage Ethiopian FTA-registered tax accountant
- [ ] Have accountant verify/enter all tax rule rates
- [ ] Set verified rules via `POST /tax-rules/verify/{id}` or database update
- [ ] Confirm 15% VAT rate effective date aligns with Proc. No. 1341/2024

### 4.2 Short-term
- [ ] Enter Employment Income Tax rates per Proc. No. 979/2016
- [ ] Enter Employee Pension rates (7% private, 11% public)
- [ ] Enter Employer Pension match rates
- [ ] Confirm Withholding Tax 2% applicability scope

### 4.3 Long-term
- [ ] Implement purchase VAT classification (recoverable vs non-recoverable)
- [ ] Add tax invoice validation at receipt upload
- [ ] Build tax filing export format (ETB, ETax system compatible)
- [ ] Add audit trail for all tax rule changes

---

## 5. Validation Commands

### 5.1 Check Current Rule Status
```bash
# List all active rules with status
curl GET http://localhost:8080/tax-rules/rules

# Verify a specific rule (requires accountant)
curl POST http://localhost:8080/tax-rules/verify/{id}
```

### 5.2 Verify Tax Summary Computation
```bash
# Generate tax summary for a month and check classification notes
curl GET "http://localhost:8080/tax/summary?month=2026-11"
```

### 5.3 Manual VAT Calculation Verification
```java
// Using TaxService.calculateReceipt()
// Validates: 15% STANDARD rate, tax-inclusive or exclusive handling
TaxCalculationDTO calc = taxService.calculateReceipt(
    new BigDecimal("1150.00"), "STANDARD", false, "Test Receipt");
 // Expected: total=1150.00, taxable=1000.00, tax=150.00
```

---

## 6. References

- **FTA Proc. No. 1341/2024** — VAT Law (standard rate, filing procedures)
- **FTA Proc. No. 979/2016** — Employment Income Tax Proclamation
- **ERCA** — Ethiopian Revenues and Customs Authority (pension and withholding)
- **Ethiopian Commercial Code** — Accounting and tax record-keeping requirements

---

*This document requires professional tax accountant review and sign-off before any compliance claims can be made.*