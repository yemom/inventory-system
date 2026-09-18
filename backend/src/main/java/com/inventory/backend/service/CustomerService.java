package com.inventory.backend.service;

import com.inventory.backend.dto.*;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerService {
    private final CustomerRepository repo;

    public Page<CustomerDTO> listCustomers(String search, Pageable pageable) {
        if (search != null && !search.isBlank())
            return repo.findByNameContainingIgnoreCase(search, pageable).map(this::toDTO);
        return repo.findAll(pageable).map(this::toDTO);
    }

    public CustomerDTO getCustomer(Long id) {
        return toDTO(repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + id)));
    }

    public CustomerDTO createCustomer(CreateCustomerRequest req) {
        String num = "CUST-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + String.format("%04d", repo.count() + 1);
        CustomerType type = CustomerType.RETAIL;
        if (req.getCustomerType() != null) {
            try { type = CustomerType.valueOf(req.getCustomerType()); } catch (Exception ignored) {}
        }
        Customer c = Customer.builder()
                .customerNumber(num).name(req.getName())
                .phone(req.getPhone()).email(req.getEmail()).address(req.getAddress())
                .customerType(type)
                .creditLimit(req.getCreditLimit() != null ? req.getCreditLimit() : BigDecimal.ZERO)
                .build();
        return toDTO(repo.save(c));
    }

    public CustomerDTO updateCustomer(Long id, UpdateCustomerRequest req) {
        Customer c = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
        if (req.getName() != null) c.setName(req.getName());
        if (req.getPhone() != null) c.setPhone(req.getPhone());
        if (req.getEmail() != null) c.setEmail(req.getEmail());
        if (req.getAddress() != null) c.setAddress(req.getAddress());
        if (req.getCreditLimit() != null) c.setCreditLimit(req.getCreditLimit());
        if (req.getCustomerType() != null) {
            try { c.setCustomerType(CustomerType.valueOf(req.getCustomerType())); } catch (Exception ignored) {}
        }
        if (req.getStatus() != null) {
            try { c.setStatus(CustomerStatus.valueOf(req.getStatus())); } catch (Exception ignored) {}
        }
        return toDTO(repo.save(c));
    }

    private CustomerDTO toDTO(Customer c) {
        return CustomerDTO.builder()
                .id(c.getId()).customerNumber(c.getCustomerNumber()).name(c.getName())
                .phone(c.getPhone()).email(c.getEmail()).address(c.getAddress())
                .customerType(c.getCustomerType().name())
                .creditLimit(c.getCreditLimit()).outstandingBalance(c.getOutstandingBalance())
                .status(c.getStatus().name()).createdAt(c.getCreatedAt())
                .build();
    }
}
