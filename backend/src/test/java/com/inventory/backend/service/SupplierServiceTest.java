package com.inventory.backend.service;

import com.inventory.backend.dto.CreateSupplierRequest;
import com.inventory.backend.dto.SupplierDTO;
import com.inventory.backend.model.Supplier;
import com.inventory.backend.repository.SupplierRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @InjectMocks
    private SupplierService supplierService;

    @Test
    @DisplayName("Create Supplier - Generates supplier number and saves")
    void createSupplier_Success() {
        CreateSupplierRequest req = CreateSupplierRequest.builder()
                .companyName("Global Tech Imports")
                .contactPerson("Abebe Kebede")
                .phone("+251911556677")
                .email("vendor@globaltech.com")
                .address("Merkato, Addis Ababa")
                .taxNumber("TIN-987654321")
                .paymentTerms("Net 30")
                .build();

        when(supplierRepository.count()).thenReturn(5L);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> {
            Supplier s = inv.getArgument(0);
            s.setId(6L);
            return s;
        });

        SupplierDTO dto = supplierService.createSupplier(req);

        assertThat(dto).isNotNull();
        assertThat(dto.getCompanyName()).isEqualTo("Global Tech Imports");
        assertThat(dto.getSupplierNumber()).startsWith("SUPP-");
        assertThat(dto.getContactPerson()).isEqualTo("Abebe Kebede");
        verify(supplierRepository).save(any(Supplier.class));
    }
}