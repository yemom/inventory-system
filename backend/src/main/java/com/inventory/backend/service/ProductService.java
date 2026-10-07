package com.inventory.backend.service;

import com.inventory.backend.dto.CreateProductRequest;
import com.inventory.backend.dto.ProductDTO;
import com.inventory.backend.dto.UpdateProductRequest;
import com.inventory.backend.model.Category;
import com.inventory.backend.model.Product;
import com.inventory.backend.model.StockMovement;
import com.inventory.backend.model.User;
import com.inventory.backend.repository.CategoryRepository;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.StockMovementRepository;
import com.inventory.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public Page<ProductDTO> listProducts(String search, Pageable pageable) {
        if (search != null && !search.trim().isEmpty()) {
            return productRepository.search(search, pageable).map(this::toDTO);
        }
        return productRepository.findAll(pageable).map(this::toDTO);
    }

    public ProductDTO getProduct(Long id) {
        return productRepository.findById(id).map(this::toDTO)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    /**
     * Creates a product and, when an opening quantity was supplied, records that
     * stock as a real movement.
     *
     * <p>The opening quantity has to do three things to be correct:
     * <ol>
     *   <li>become {@code Product.quantity}, so the product reads 50 on hand
     *       instead of 0 and is not flagged out of stock;</li>
     *   <li>appear in stock history — an opening balance that exists only as a
     *       number on the product row cannot be reconciled against movements,
     *       and an unexplained gap in the ledger is exactly what a stock count
     *       is meant to find;</li>
     *   <li>be attributable, so the ledger says who opened the product and when.</li>
     * </ol>
     *
     * <p>All three happen in this one transaction, so a failure cannot leave a
     * product with stock that no movement accounts for, or a movement for a
     * product that was never saved.
     *
     * <p>A negative opening quantity is rejected rather than clamped: a request
     * for -5 is a mistake, and silently turning it into 0 would hide it. An
     * opening balance of 0 records no movement, since there is nothing to record.
     */
    public ProductDTO createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new IllegalArgumentException("Product with this SKU already exists");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        Integer openingQuantity = request.getInitialQuantity();
        if (openingQuantity != null && openingQuantity < 0) {
            throw new IllegalArgumentException("Opening quantity cannot be negative.");
        }

        Product product = new Product();
        product.setSku(request.getSku());
        product.setBarcode(request.getBarcode());
        product.setName(request.getName());
        product.setUnit(request.getUnit());
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
        // Opening stock is the quantity on hand from the moment the product exists.
        product.setQuantity(openingQuantity != null ? openingQuantity : 0);

        Product saved = productRepository.save(product);

        if (openingQuantity != null && openingQuantity > 0) {
            User actor = currentUser();
            stockMovementRepository.save(StockMovement.builder()
                    .product(saved)
                    .type(StockMovement.TYPE_OPENING)
                    .quantity(openingQuantity)
                    .reference(saved.getSku())
                    .notes("Opening stock recorded when the product was created")
                    .createdBy(actor)
                    .status(StockMovement.Status.APPROVED)
                    .approvedAt(java.time.LocalDateTime.now())
                    .approvedBy(actor)
                    .build());

            auditLogService.log(actor == null ? null : actor.getId(),
                    actor == null ? "system" : actor.getUsername(),
                    "PRODUCT_OPENING_STOCK", "PRODUCT", String.valueOf(saved.getId()),
                    "Created " + saved.getSku() + " with opening stock of " + openingQuantity
                            + " " + (saved.getUnit() == null ? "units" : saved.getUnit()));
        }

        return toDTO(saved);
    }

    private boolean changesPricing(Product product, UpdateProductRequest request) {
        return isDifferent(request.getSellingPrice(), product.getSellingPrice())
                || isDifferent(request.getPurchasePrice(), product.getPurchasePrice())
                || isDifferent(request.getMinSellingPrice(), product.getMinSellingPrice());
    }

    private boolean isDifferent(java.math.BigDecimal requested, java.math.BigDecimal current) {
        return requested != null && (current == null || requested.compareTo(current) != 0);
    }

    private void requireAuthority(String authority, String message) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        boolean granted = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
        if (!granted) {
            throw new org.springframework.security.access.AccessDeniedException(message);
        }
    }

    private User currentUser() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        String principal = authentication.getName();
        return userRepository.findByEmail(principal)
                .or(() -> userRepository.findByUsername(principal))
                .orElse(null);
    }

    /**
     * Updates a product.
     *
     * <p>Pricing is separated from the rest of the edit on purpose. Only roles
     * holding {@code PRICE_CHANGE_APPROVE} (Manager, Super Admin) may change a
     * price; editing a name or reorder level does not require it. The check is in
     * the service rather than only on the endpoint so that adding a second route
     * to update a product cannot quietly reopen the ability to re-price as a
     * side effect.
     */
    public ProductDTO updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        if (changesPricing(product, request)) {
            requireAuthority("PRICE_CHANGE_APPROVE",
                    "You do not have permission to change prices.");
            User actor = currentUser();
            BigDecimal previousSelling = product.getSellingPrice();
            auditLogService.log(actor == null ? null : actor.getId(),
                    actor == null ? "system" : actor.getUsername(),
                    "PRODUCT_PRICE_CHANGED", "PRODUCT", String.valueOf(id),
                    "Selling price " + previousSelling + " -> " + request.getSellingPrice());
        }

        if (request.getSku() != null) {
            if (!product.getSku().equals(request.getSku()) && productRepository.existsBySku(request.getSku())) {
                throw new IllegalArgumentException("Product with this SKU already exists");
            }
            product.setSku(request.getSku());
        }
        if (request.getBarcode() != null) product.setBarcode(request.getBarcode());
        if (request.getName() != null) product.setName(request.getName());
        if (request.getUnit() != null) product.setUnit(request.getUnit());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));
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
        // Soft delete — preserve inventory history
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        product.setActive(false);
        productRepository.save(product);
    }

    public ProductDTO toDTO(Product product) {
        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setSku(product.getSku());
        dto.setBarcode(product.getBarcode());
        dto.setName(product.getName());
        dto.setUnit(product.getUnit());
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
        dto.setQuantity(product.getQuantity() != null ? product.getQuantity() : 0);
        dto.setBatchTracked(product.isBatchTracked());
        dto.setExpiryTracked(product.isExpiryTracked());
        dto.setActive(product.isActive());
        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());
        return dto;
    }
}
