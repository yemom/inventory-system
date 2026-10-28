package com.inventory.backend.config;

import com.inventory.backend.model.TaxRule;
import com.inventory.backend.repository.TaxRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Seeds review-required defaults; a finance administrator must verify them before a period can finalize. */
@Component @RequiredArgsConstructor
public class TaxRuleBootstrap {
    private final TaxRuleRepository rules;
    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        seed("VAT_STANDARD", "Standard VAT", "VAT", "0.15", "https://www.mofed.gov.et/media/filer_public/af/45/af45af2f-7959-4e8b-b736-9494dda9f017/vat_proclamation_no_1341-2016_with_annex.pdf", "Article 8; standard taxable supplies only.");
        seed("PENSION_EMPLOYEE", "Employee pension", "EMPLOYEE_PENSION", "0.07", "https://ethiodata.et/wp-content/uploads/2023/01/Ethiopia-Private-Organization-Employees-Pension-Proclamation-No.-1268_2022.pdf", "Private-organization eligibility must be confirmed.");
        seed("PENSION_EMPLOYER", "Employer pension", "EMPLOYER_PENSION", "0.11", "https://ethiodata.et/wp-content/uploads/2023/01/Ethiopia-Private-Organization-Employees-Pension-Proclamation-No.-1268_2022.pdf", "Private-organization eligibility must be confirmed.");
        seed("EMPLOYMENT_BRACKET_2000", "Employment income 0-2,000", "EMPLOYMENT_INCOME_TAX", "0.00", "https://www.metaappz.com/pdf/ethiopian_laws/federal/2025/pr_1395_2025.pdf", "upperBound=2000; Article 11 amendment; accountant verification required.");
        seed("EMPLOYMENT_BRACKET_4000", "Employment income 2,001-4,000", "EMPLOYMENT_INCOME_TAX", "0.10", "https://www.metaappz.com/pdf/ethiopian_laws/federal/2025/pr_1395_2025.pdf", "upperBound=4000; accountant verification required.");
        seed("EMPLOYMENT_BRACKET_7000", "Employment income 4,001-7,000", "EMPLOYMENT_INCOME_TAX", "0.15", "https://www.metaappz.com/pdf/ethiopian_laws/federal/2025/pr_1395_2025.pdf", "upperBound=7000; accountant verification required.");
        seed("EMPLOYMENT_BRACKET_10000", "Employment income 7,001-10,000", "EMPLOYMENT_INCOME_TAX", "0.20", "https://www.metaappz.com/pdf/ethiopian_laws/federal/2025/pr_1395_2025.pdf", "upperBound=10000; accountant verification required.");
        seed("EMPLOYMENT_BRACKET_14000", "Employment income 10,001-14,000", "EMPLOYMENT_INCOME_TAX", "0.25", "https://www.metaappz.com/pdf/ethiopian_laws/federal/2025/pr_1395_2025.pdf", "upperBound=14000; accountant verification required.");
        seed("EMPLOYMENT_BRACKET_INFINITY", "Employment income above 14,000", "EMPLOYMENT_INCOME_TAX", "0.35", "https://www.metaappz.com/pdf/ethiopian_laws/federal/2025/pr_1395_2025.pdf", "upperBound=INFINITY; confirm any intermediate bracket before finalizing.");
    }
    private void seed(String code, String name, String type, String rate, String source, String notes) {
        if (!rules.findEffective(code, LocalDate.now()).isEmpty()) return;
        rules.save(TaxRule.builder().code(code).name(name).type(type).rate(new BigDecimal(rate)).effectiveFrom(LocalDate.of(2025, 1, 1)).verificationStatus("ACCOUNTANT_REVIEW_REQUIRED").sourceUrl(source).notes(notes).active(true).build());
    }
}
