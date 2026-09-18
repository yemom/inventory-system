package com.inventory.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.backend.dto.CreateSupplierRequest;
import com.inventory.backend.dto.SupplierDTO;
import com.inventory.backend.security.JwtAuthenticationFilter;
import com.inventory.backend.security.JwtUtil;
import com.inventory.backend.service.SupplierService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupplierController.class)
@AutoConfigureMockMvc(addFilters = false)
class SupplierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SupplierService supplierService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private SupplierDTO sampleSupplier;

    @BeforeEach
    void setUp() {
        sampleSupplier = SupplierDTO.builder()
                .id(1L)
                .supplierNumber("SUPP-20260913-0001")
                .companyName("Global Importers")
                .contactPerson("Sara T.")
                .phone("+251911999999")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/suppliers - Returns page of suppliers")
    void listSuppliers_Success() throws Exception {
        when(supplierService.listSuppliers(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleSupplier)));

        mockMvc.perform(get("/api/v1/suppliers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].companyName").value("Global Importers"));
    }

    @Test
    @DisplayName("POST /api/v1/suppliers - Creates supplier")
    void createSupplier_Success() throws Exception {
        CreateSupplierRequest req = CreateSupplierRequest.builder()
                .companyName("Global Importers")
                .contactPerson("Sara T.")
                .phone("+251911999999")
                .build();

        when(supplierService.createSupplier(any(CreateSupplierRequest.class))).thenReturn(sampleSupplier);

        mockMvc.perform(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Supplier created"))
                .andExpect(jsonPath("$.data.companyName").value("Global Importers"));
    }
}