package com.inventory.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.backend.dto.CreateCustomerRequest;
import com.inventory.backend.dto.CustomerDTO;
import com.inventory.backend.security.JwtAuthenticationFilter;
import com.inventory.backend.security.JwtUtil;
import com.inventory.backend.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CustomerService customerService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private CustomerDTO sampleCustomer;

    @BeforeEach
    void setUp() {
        sampleCustomer = CustomerDTO.builder()
                .id(1L)
                .customerNumber("CUST-20260913-0001")
                .name("Retail Client")
                .phone("+251911000000")
                .customerType("RETAIL")
                .creditLimit(BigDecimal.ZERO)
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/customers - Returns page of customers")
    void listCustomers_Success() throws Exception {
        when(customerService.listCustomers(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleCustomer)));

        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].customerNumber").value("CUST-20260913-0001"));
    }

    @Test
    @DisplayName("POST /api/v1/customers - Creates customer")
    void createCustomer_Success() throws Exception {
        CreateCustomerRequest req = CreateCustomerRequest.builder()
                .name("Retail Client")
                .phone("+251911000000")
                .customerType("RETAIL")
                .build();

        when(customerService.createCustomer(any(CreateCustomerRequest.class))).thenReturn(sampleCustomer);

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Customer created"))
                .andExpect(jsonPath("$.data.name").value("Retail Client"));
    }
}