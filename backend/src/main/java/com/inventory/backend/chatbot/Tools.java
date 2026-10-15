package com.inventory.backend.chatbot;

import java.util.List;
import java.util.Map;

/**
 * Which tools an intent needs.
 *
 * <p>Kept apart from the registry so the mapping from a question to a set of
 * read-only lookups is visible in one place and testable without Spring. If an
 * intent ever needs a write, this is the list that would have to change — and it
 * is empty of them.
 */
final class Tools {

    private Tools() {
    }

    /**
     * The tools that answer an intent.
     *
     * <p>Every entry here is a read-only lookup. An intent that maps to nothing
     * returns an empty list, which the service reports as "I do not know how to
     * answer that" rather than guessing.
     */
    static List<String> forIntent(String intent) {
        return switch (intent) {
            case "NET_PROFIT", "GROSS_PROFIT" -> List.of("profit");
            case "FINANCIAL_COMPARISON" -> List.of("financial_comparison");
            case "CASH_POSITION" -> List.of("cash_position");
            case "RECEIVABLES" -> List.of("receivables");
            case "PAYABLES" -> List.of("payables");
            case "INVENTORY_VALUE" -> List.of("inventory_value");
            case "STOCKOUT_RISK" -> List.of("stockout_risk");
            case "SLOW_MOVING" -> List.of("slow_moving");
            case "SEARCH_PRODUCTS" -> List.of("search_products");
            case "PRODUCT_AVAILABILITY" -> List.of("product_availability");
            case "PRODUCT_ALTERNATIVES" -> List.of("product_alternatives");
            case "CUSTOMER_ORDER_STATUS" -> List.of("customer_order_status");
            case "CUSTOMER_ORDER_HISTORY" -> List.of("customer_order_history");
            case "STORE_POLICIES" -> List.of("store_policies");
            case "PAYMENT_METHODS" -> List.of("payment_methods");
            case "HOW_TO_ORDER" -> List.of("how_to_order");
            case "RAISE_SUPPORT_REQUEST" -> List.of("raise_support_request");
            default -> List.of();
        };
    }
}
