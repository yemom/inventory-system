package com.inventory.backend.chatbot.product;

import com.inventory.backend.chatbot.ChatbotTool;
import com.inventory.backend.model.Product;
import com.inventory.backend.repository.CategoryRepository;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.service.WarehouseStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Product questions, for customers and staff.
 *
 * <h2>Stock availability is per warehouse and is not oversold by the chat</h2>
 *
 * <p>A customer asks "do you have any?" and the honest answer depends on which
 * warehouse they mean, or on the business total if they do not care. Both are
 * returned, labelled, rather than one being silently chosen.
 *
 * <p>The figure is the quantity currently recorded — not a promise that it is
 * still on the shelf when the customer arrives. The answer says so. A chatbot
 * that states a number as certainty about physical stock is inventing the one
 * thing it cannot know.
 *
 * <h2>Alternatives are found by category and price, never by guessing</h2>
 *
 * <p>"What else do you like?" is not answerable from a catalogue. "What else is
 * in this category, under this price" is. So the alternative tool takes a real
 * filter and returns real rows, and the suggestion chips offer filters rather
 * than asking the assistant to recommend.
 */
@Component
@RequiredArgsConstructor
public class ProductTools {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final WarehouseStockService warehouseStockService;

    /**
     * Searches the catalogue.
     *
     * <p>Matches name, SKU, category and description. Only active products are
     * returned: a discontinued product is not a product a customer can buy, and
     * offering one would be inventing availability.
     */
    public Map<String, Object> search(Map<String, Object> params, ChatbotTool.ToolContext ctx) {
        String term = text(params, "query");
        if (term == null || term.isBlank()) {
            return Map.of("results", List.of(), "note", "A search term is needed.");
        }

        String needle = term.toLowerCase(Locale.ROOT);
        List<Product> matches = productRepository.findAllActive().stream()
                .filter(p -> matches(p, needle))
                .toList();

        if (matches.isEmpty()) {
            // Saying "nothing matched" is a real answer. Recommending something
            // loosely related instead would be the assistant guessing at stock.
            return Map.of("results", List.of(), "matched", 0,
                    "note", "No active product matches '" + term + "'.");
        }

        List<Map<String, Object>> rows = matches.stream()
                .limit(10)
                .map(this::toRow)
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("results", rows);
        out.put("matched", matches.size());
        out.put("searched", term);
        return out;
    }

    /**
     * Availability for one product, per warehouse and in total.
     *
     * <p>Used for the "is this in stock" question and for the alternative finder.
     */
    public Map<String, Object> availability(Map<String, Object> params, ChatbotTool.ToolContext ctx) {
        Long productId = number(params, "productId");
        if (productId == null) {
            return Map.of("results", List.of(), "note", "A product id is needed.");
        }
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || !product.isActive()) {
            return Map.of("results", List.of(), "available", false,
                    "note", "That product is not currently sold.");
        }

        int total = product.getQuantity() == null ? 0 : product.getQuantity();
        List<Map<String, Object>> byWarehouse = warehouseStockService.stockOfProduct(productId).stream()
                .map(stock -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("warehouse", stock.getWarehouse() == null
                            ? "unassigned" : stock.getWarehouse().getName());
                    row.put("units", stock.getQuantity());
                    return row;
                })
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("productId", product.getId());
        out.put("name", product.getName());
        out.put("sku", product.getSku());
        out.put("unit", product.getUnit());
        out.put("price", product.getSellingPrice());
        out.put("totalUnits", total);
        out.put("available", total > 0);
        out.put("byWarehouse", byWarehouse);
        out.put("availabilityIsRecordedStock", true);
        out.put("note", "Recorded stock, not a guarantee it is still on the shelf.");
        return out;
    }

    /**
     * Alternatives: same category, or within a price band.
     *
     * <p>Both filters are optional and can be combined. A category match is worth
     * more than a price match, so category-only results come first.
     */
    public Map<String, Object> alternatives(Map<String, Object> params, ChatbotTool.ToolContext ctx) {
        Long productId = number(params, "productId");
        Product reference = productId == null ? null : productRepository.findById(productId).orElse(null);
        if (reference == null) {
            return Map.of("results", List.of(), "note", "A product id is needed.");
        }

        String categoryName = reference.getCategory() == null ? null : reference.getCategory().getName();
        BigDecimal ceiling = decimal(params, "maxPrice");

        List<Product> candidates = productRepository.findAllActive().stream()
                .filter(p -> !p.getId().equals(reference.getId()))
                .toList();

        List<Map<String, Object>> sameCategory = new ArrayList<>();
        List<Map<String, Object>> withinBudget = new ArrayList<>();

        for (Product candidate : candidates) {
            String candidateCategory = candidate.getCategory() == null ? null : candidate.getCategory().getName();
            boolean categoryMatch = categoryName != null && categoryName.equals(candidateCategory);
            BigDecimal price = candidate.getSellingPrice();
            boolean priceMatch = ceiling != null && price != null
                    && price.compareTo(ceiling) <= 0
                    && price.compareTo(BigDecimal.ZERO) > 0;

            // Category first, and a product that matches both is listed once, in
            // the more meaningful list — showing it twice reads as a ranking the
            // assistant did not make.
            if (categoryMatch) {
                sameCategory.add(toRow(candidate));
            } else if (priceMatch) {
                withinBudget.add(toRow(candidate));
            }
        }

        // Worth is the same unit of stock across both lists, so a reader can
        // compare them.
        sameCategory.sort(Comparator.comparing(r -> String.valueOf(r.get("name"))));
        withinBudget.sort(Comparator.comparing(r -> String.valueOf(r.get("name"))));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("because", reference.getName() + " is unavailable");
        out.put("sameCategory", sameCategory.stream().limit(5).toList());
        out.put("withinBudget", withinBudget.stream().limit(5).toList());
        out.put("category", categoryName);
        out.put("budget", ceiling);
        out.put("note", "Alternatives share a category or fit the budget. They are "
                + "catalogue facts, not a statement that the assistant endorses one.");
        return out;
    }

    /** Every category, for the "what do you sell" question and the suggestion chips. */
    public List<Map<String, Object>> categories() {
        return categoryRepository.findAll().stream()
                .map(category -> Map.<String, Object>of(
                        "id", category.getId(),
                        "name", category.getName()))
                .toList();
    }

    private boolean matches(Product product, String needle) {
        return contains(product.getName(), needle)
                || contains(product.getSku(), needle)
                || contains(product.getDescription(), needle)
                || (product.getCategory() != null && contains(product.getCategory().getName(), needle));
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private Map<String, Object> toRow(Product product) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("productId", product.getId());
        row.put("name", product.getName());
        row.put("sku", product.getSku());
        row.put("category", product.getCategory() == null ? "uncategorised" : product.getCategory().getName());
        row.put("description", product.getDescription());
        row.put("price", product.getSellingPrice());
        row.put("unit", product.getUnit());
        row.put("inStock", product.getQuantity() != null && product.getQuantity() > 0);
        row.put("unitsOnHand", product.getQuantity() == null ? 0 : product.getQuantity());
        return row;
    }

    // ── Argument coercion ──────────────────────────────────────────────────────

    public static String text(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    public static Long number(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static BigDecimal decimal(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer integer(Map<String, Object> params, String key, int fallback) {
        Long value = number(params, key);
        return value == null ? fallback : value.intValue();
    }
}
