# Ethiopian Tax and Payroll Rule Register

Last reviewed: 2026-10-27. Jurisdiction: Federal Democratic Republic of Ethiopia. Currency: ETB.

This register is an application configuration baseline, not legal advice. Every seeded rule is marked `ACCOUNTANT_REVIEW_REQUIRED`; the payroll service refuses to finalize a period until an authorized finance user changes the applicable rules to `VERIFIED` after checking the business's registration, taxpayer category, exemptions, and effective dates.

| Rule | Type | Effective from | Calculation | Source | Status |
| --- | --- | --- | --- | --- | --- |
| VAT standard rate | VAT | 2025-01-01 baseline | 15% only for standard-rated taxable supplies | [VAT Proclamation No. 1341/2024, Art. 8](https://www.mofed.gov.et/media/filer_public/af/45/af45af2f-7959-4e8b-b736-9494dda9f017/vat_proclamation_no_1341-2016_with_annex.pdf) | Accountant review required |
| VAT zero-rated treatment | VAT | See governing rule | 0%; not exempt treatment | [VAT Proclamation No. 1341/2024, Art. 8](https://www.mofed.gov.et/media/filer_public/af/45/af45af2f-7959-4e8b-b736-9494dda9f017/vat_proclamation_no_1341-2016_with_annex.pdf) | Configuration required per supply |
| Employee pension | Pension | Current verified applicability | 7% of eligible basic salary | [Private Organizations Employees' Pension Proclamation No. 1268/2022](https://ethiodata.et/wp-content/uploads/2023/01/Ethiopia-Private-Organization-Employees-Pension-Proclamation-No.-1268_2022.pdf) | Accountant review required |
| Employer pension | Pension | Current verified applicability | 11% of eligible basic salary; employer cost, not employee deduction | [Private Organizations Employees' Pension Proclamation No. 1268/2022](https://ethiodata.et/wp-content/uploads/2023/01/Ethiopia-Private-Organization-Employees-Pension-Proclamation-No.-1268_2022.pdf) | Accountant review required |
| Employment income tax brackets | Employment income tax | 2025 amendment baseline | Effective-dated progressive bands stored as `EMPLOYMENT_BRACKET_*` tax rules | [Income Tax Amendment Proclamation No. 1395/2025, Art. 11](https://www.metaappz.com/pdf/ethiopian_laws/federal/2025/pr_1395_2025.pdf) | Accountant review required |

## Application behaviour

- Tax summaries report persisted sale and purchase `tax` fields only. They label input VAT as an estimate because recoverability cannot be inferred from a generic purchase record.
- Sales, purchases, expenses, payments, and tax rules are not rewritten by this module.
- A payroll run snapshots salary, pension, income-tax result, and its rule-version string in each payroll item. Finalized periods cannot be finalized twice.
- No withholding or business-income-tax rule is seeded: taxpayer category, transaction classification, and supporting directive conditions are not available in StockFlow. The system therefore does not manufacture those liabilities.

## Required finance configuration before production use

1. Review every rule in Tax Management against current official gazette/directive material and set the correct effective end dates.
2. Add any missing employment-tax bracket(s), thresholds, and applicable non-taxable earnings treatment before marking the employment rules `VERIFIED`.
3. Configure product and supplier tax classifications before treating the VAT estimate as a return-ready reconciliation.
4. Confirm pension eligibility per employee and restrict payroll-detail access to authorized finance roles.
