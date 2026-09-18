package com.inventory.backend.service;

import com.inventory.backend.dto.CreateCustomerRequest;
import com.inventory.backend.dto.CustomerDTO;
import com.inventory.backend.model.Customer;
import com.inventory.backend.model.CustomerStatus;
import com.inventory.backend.model.CustomerType;
import com.inventory.backend.repository.CustomerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    @DisplayName("Create Customer - Generates customer number and saves")
    void createCustomer_Success() {
        CreateCustomerRequest req = CreateCustomerRequest.builder()
                .name("Acme Retailers")
                .phone("+251911223344")
                .email("acme@example.com")
                .address("Bole, Addis Ababa")
                .customerType("BUSINESS")
                .creditLimit(new BigDecimal("50000.00"))
                .build();

        when(customerRepository.count()).thenReturn(15L);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(16L);
            return c;
        });

        CustomerDTO dto = customerService.createCustomer(req);

        assertThat(dto).isNotNull();
        assertThat(dto.getName()).isEqualTo("Acme Retailers");
        assertThat(dto.getCustomerNumber()).startsWith("CUST-");
        assertThat(dto.getCustomerType()).isEqualTo("BUSINESS");
        assertThat(dto.getCreditLimit()).isEqualByComparingTo("50000.00");
        verify(customerRepository).save(any(Customer.class));
    }
}