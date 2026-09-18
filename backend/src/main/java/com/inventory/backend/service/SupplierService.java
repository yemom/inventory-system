package com.inventory.backend.service;

import com.inventory.backend.dto.*;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.SupplierRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Transactional
public class SupplierService {
    private final SupplierRepository repo;

    public Page<SupplierDTO> listSuppliers(String search, Pageable pageable) {
        if (search != null && !search.isBlank())
            return repo.findByCompanyNameContainingIgnoreCase(search, pageable).map(this::toDTO);
        return repo.findAll(pageable).map(this::toDTO);
    }

    public SupplierDTO getSupplier(Long id) {
        return toDTO(repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Supplier not found: " + id)));
    }

    public SupplierDTO createSupplier(CreateSupplierRequest req) {
        String num = "SUPP-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + String.format("%04d", repo.count() + 1);
        Supplier s = Supplier.builder()
                .supplierNumber(num).companyName(req.getCompanyName())
                .contactPerson(req.getContactPerson()).phone(req.getPhone())
                .email(req.getEmail()).address(req.getAddress())
                .taxNumber(req.getTaxNumber()).paymentTerms(req.getPaymentTerms())
                .build();
        return toDTO(repo.save(s));
    }

    public SupplierDTO updateSupplier(Long id, UpdateSupplierRequest req) {
        Supplier s = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Supplier not found"));
        if (req.getCompanyName() != null) s.setCompanyName(req.getCompanyName());
        if (req.getContactPerson() != null) s.setContactPerson(req.getContactPerson());
        if (req.getPhone() != null) s.setPhone(req.getPhone());
        if (req.getEmail() != null) s.setEmail(req.getEmail());
        if (req.getAddress() != null) s.setAddress(req.getAddress());
        if (req.getTaxNumber() != null) s.setTaxNumber(req.getTaxNumber());
        if (req.getPaymentTerms() != null) s.setPaymentTerms(req.getPaymentTerms());
        if (req.getStatus() != null) {
            try { s.setStatus(SupplierStatus.valueOf(req.getStatus())); } catch (Exception ignored) {}
        }
        return toDTO(repo.save(s));
    }

    private SupplierDTO toDTO(Supplier s) {
        return SupplierDTO.builder()
                .id(s.getId()).supplierNumber(s.getSupplierNumber())
                .companyName(s.getCompanyName()).contactPerson(s.getContactPerson())
                .phone(s.getPhone()).email(s.getEmail()).address(s.getAddress())
                .taxNumber(s.getTaxNumber()).paymentTerms(s.getPaymentTerms())
                .outstandingBalance(s.getOutstandingBalance())
                .status(s.getStatus().name()).createdAt(s.getCreatedAt())
                .build();
    }
}
