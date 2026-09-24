package com.inventory.backend.service;

import com.inventory.backend.dto.WarehouseDTO;
import com.inventory.backend.dto.CreateWarehouseRequest;
import com.inventory.backend.model.Warehouse;
import com.inventory.backend.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class WarehouseService {
    private final WarehouseRepository warehouseRepository;

    public List<WarehouseDTO> listWarehouses() {
        return warehouseRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public WarehouseDTO getWarehouse(Long id) {
        return warehouseRepository.findById(id).map(this::toDTO)
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found"));
    }

    public WarehouseDTO createWarehouse(CreateWarehouseRequest request) {
        if (warehouseRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Warehouse with this name already exists");
        }
        Warehouse w = new Warehouse();
        w.setName(request.getName());
        w.setLocation(request.getLocation());
        w.setManagerName(request.getManagerName());
        w.setContactPhone(request.getContactPhone());
        return toDTO(warehouseRepository.save(w));
    }

    public WarehouseDTO updateWarehouse(Long id, CreateWarehouseRequest request) {
        Warehouse w = warehouseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found"));
        
        if (!w.getName().equals(request.getName()) && warehouseRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Warehouse with this name already exists");
        }
        
        w.setName(request.getName());
        w.setLocation(request.getLocation());
        w.setManagerName(request.getManagerName());
        w.setContactPhone(request.getContactPhone());
        return toDTO(warehouseRepository.save(w));
    }

    public void deleteWarehouse(Long id) {
        Warehouse w = warehouseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found"));
        w.setActive(false);
        warehouseRepository.save(w);
    }

    private WarehouseDTO toDTO(Warehouse w) {
        WarehouseDTO dto = new WarehouseDTO();
        dto.setId(w.getId());
        dto.setName(w.getName());
        dto.setLocation(w.getLocation());
        dto.setManagerName(w.getManagerName());
        dto.setContactPhone(w.getContactPhone());
        dto.setActive(w.isActive());
        dto.setCreatedAt(w.getCreatedAt());
        return dto;
    }
}
