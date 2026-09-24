package com.inventory.backend.service;

import com.inventory.backend.dto.CreateProductRequest;
import com.inventory.backend.dto.ProductDTO;
import com.inventory.backend.dto.UpdateProductRequest;
import com.inventory.backend.model.Category;
import com.inventory.backend.model.Product;
import com.inventory.backend.repository.CategoryRepository;
import com.inventory.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public Page<ProductDTO> listProducts(String search, Pageable pageable) {
        if (search != null && !search.trim().isEmpty()) {
            return productRepository.search(search, pageable).map(this::toDTO);
        }
        return productRepository.findAll(pageable).map(this::toDTO);
    }

    public ProductDTO getProduct(Long id) {
        return productRepository.findById(id).map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    public ProductDTO createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new RuntimeException("Product with this SKU already exists");
        }
        
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        Product product = new Product();
        product.setSku(request.getSku());
        product.setBarcode(request.getBarcode());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategory(category);
        product.setPurchasePrice(request.getPurchasePrice());
        product.setSellingPrice(request.getSellingPrice());
        product.setMinSellingPrice(request.getMinSellingPrice());
        product.setMinStockLevel(request.getMinStockLevel());
        product.setMaxStockLevel(request.getMaxStockLevel());
        product.setReorderLevel(request.getReorderLevel());
        product.setBatchTracked(request.isBatchTracked());
        product.setExpiryTracked(request.isExpiryTracked());
        product.setActive(true);

        return toDTO(productRepository.save(product));
    }

    public ProductDTO updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (request.getSku() != null) {
            if (!product.getSku().equals(request.getSku()) && productRepository.existsBySku(request.getSku())) {
                throw new RuntimeException("Product with this SKU already exists");
            }
            product.setSku(request.getSku());
        }
        if (request.getBarcode() != null) product.setBarcode(request.getBarcode());
        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            product.setCategory(category);
        }
        if (request.getPurchasePrice() != null) product.setPurchasePrice(request.getPurchasePrice());
        if (request.getSellingPrice() != null) product.setSellingPrice(request.getSellingPrice());
        if (request.getMinSellingPrice() != null) product.setMinSellingPrice(request.getMinSellingPrice());
        if (request.getMinStockLevel() != null) product.setMinStockLevel(request.getMinStockLevel());
        if (request.getMaxStockLevel() != null) product.setMaxStockLevel(request.getMaxStockLevel());
        if (request.getReorderLevel() != null) product.setReorderLevel(request.getReorderLevel());
        if (request.getBatchTracked() != null) product.setBatchTracked(request.getBatchTracked());
        if (request.getExpiryTracked() != null) product.setExpiryTracked(request.getExpiryTracked());
        if (request.getActive() != null) product.setActive(request.getActive());

        return toDTO(productRepository.save(product));
    }
    
    public void deleteProduct(Long id) {
        // Soft delete
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        product.setActive(false);
        productRepository.save(product);
    }

    private ProductDTO toDTO(Product product) {
        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setSku(product.getSku());
        dto.setBarcode(product.getBarcode());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        if (product.getCategory() != null) {
            dto.setCategoryId(product.getCategory().getId());
            dto.setCategoryName(product.getCategory().getName());
        }
        dto.setPurchasePrice(product.getPurchasePrice());
        dto.setSellingPrice(product.getSellingPrice());
        dto.setMinSellingPrice(product.getMinSellingPrice());
        dto.setMinStockLevel(product.getMinStockLevel());
        dto.setMaxStockLevel(product.getMaxStockLevel());
        dto.setReorderLevel(product.getReorderLevel());
        dto.setBatchTracked(product.isBatchTracked());
        dto.setExpiryTracked(product.isExpiryTracked());
        dto.setActive(product.isActive());
        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());
        return dto;
    }
}
