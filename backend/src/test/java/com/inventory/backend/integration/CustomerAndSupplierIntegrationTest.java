package com.inventory.backend.integration;

import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.dto.CreateCustomerRequest;
import com.inventory.backend.dto.CreateSupplierRequest;
import com.inventory.backend.dto.CustomerDTO;
import com.inventory.backend.dto.SupplierDTO;
import com.inventory.backend.dto.UpdateCustomerRequest;
import com.inventory.backend.dto.UpdateSupplierRequest;
import com.inventory.backend.model.Customer;
import com.inventory.backend.model.Supplier;
import com.inventory.backend.repository.CustomerRepository;
import com.inventory.backend.repository.SupplierRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerAndSupplierIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Test
    @DisplayName("Integration: Full Customer lifecycle with auto-generated code and DB persistence")
    void customerLifecycle_Success() throws Exception {
        String token = loginAsSuperAdmin();

        // 1. Create customer
        CreateCustomerRequest createReq = CreateCustomerRequest.builder()
                .name("Abyssinia Trading PLC")
                .phone("+251911334455")
                .email("info@abyssiniatrading.et")
                .address("Piazza, Addis Ababa")
                .customerType("WHOLESALE")
                .creditLimit(new BigDecimal("100000.00"))
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .headers(authHeader(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customerNumber").isNotEmpty())
                .andReturn();

        Customer customerInDb = customerRepository.findAll().stream()
                .filter(c -> "Abyssinia Trading PLC".equals(c.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(customerInDb.getCustomerNumber()).startsWith("CUST-");
        assertThat(customerInDb.getCreditLimit()).isEqualByComparingTo("100000.00");
        assertThat(customerInDb.getOutstandingBalance()).isEqualByComparingTo("0.00");

        // 2. Query list
        mockMvc.perform(get("/api/v1/customers?search=Abyssinia")
                        .headers(authHeader(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Abyssinia Trading PLC"));
    }

    @Test
    @DisplayName("Integration: Full Supplier lifecycle with auto-generated code and DB persistence")
    void supplierLifecycle_Success() throws Exception {
        String token = loginAsSuperAdmin();

        // 1. Create supplier
        CreateSupplierRequest createReq = CreateSupplierRequest.builder()
                .companyName("East Africa Paper & Packaging")
                .contactPerson("Dawit Mengistu")
                .phone("+251912445566")
                .email("dawit@eastafricapaper.et")
                .address("Kaliti Industrial Zone, Addis Ababa")
                .taxNumber("TIN-1122334455")
                .paymentTerms("Net 45")
                .build();

        mockMvc.perform(post("/api/v1/suppliers")
                        .headers(authHeader(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.supplierNumber").isNotEmpty())
                .andExpect(jsonPath("$.data.companyName").value("East Africa Paper & Packaging"));

        Supplier supplierInDb = supplierRepository.findAll().stream()
                .filter(s -> "East Africa Paper & Packaging".equals(s.getCompanyName()))
                .findFirst()
                .orElseThrow();

        assertThat(supplierInDb.getSupplierNumber()).startsWith("SUPP-");
        assertThat(supplierInDb.getTaxNumber()).isEqualTo("TIN-1122334455");
        assertThat(supplierInDb.getOutstandingBalance()).isEqualByComparingTo("0.00");

        // 2. Query list
        mockMvc.perform(get("/api/v1/suppliers?search=Paper")
                        .headers(authHeader(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].companyName").value("East Africa Paper & Packaging"));
    }
}