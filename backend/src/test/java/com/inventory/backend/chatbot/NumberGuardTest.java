package com.inventory.backend.chatbot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The guard that keeps a phrasing layer from inventing figures.
 *
 * <p>Every rule here exists because a language model asked to write
 * a sentence about a tool result will sometimes write a number that
 * was not in the result — a rounding slip, or a figure from an
 * earlier question. These tests are the guard's specification: a
 * number the tools did not return is not "close enough", it is
 * discarded.
 */
class NumberGuardTest {

    private static final Set<BigDecimal> ALLOWED = Set.of(
            new BigDecimal("12400.00"),
            new BigDecimal("3200"),
            new BigDecimal("-150.25"),
            new BigDecimal("0.00"));

    @Test
    @DisplayName("a figure from the tool results is accepted")
    void acceptsFiguresFromTheToolResults() {
        assertTrue(NumberGuard.numbersAreSafe(
                "Net profit was ETB 12,400.00.", ALLOWED, Set.of()),
                "12,400.00 is in the tool results");
        assertTrue(NumberGuard.numbersAreSafe(
                "Net profit was ETB 12400.", ALLOWED, Set.of()),
                "the same figure without separators");
    }

    @Test
    @DisplayName("a figure the tools did not return is refused")
    void refusesInventedFigures() {
        assertFalse(NumberGuard.numbersAreSafe(
                "Net profit was ETB 13,500.00.", ALLOWED, Set.of()),
                "13,500.00 is not in the tool results");
        assertFalse(NumberGuard.numbersAreSafe(
                "Sales rose to ETB 12.4 million.", ALLOWED, Set.of()),
                "12,400,000 scaled differently is still not the figure returned");
    }

    @Test
    @DisplayName("a rounding slip is refused")
    void refusesRoundingSlips() {
        assertFalse(NumberGuard.numbersAreSafe(
                "Net profit was ETB 12.4k.", ALLOWED, Set.of()),
                "12,400 rounded to 12.4k changes the figure even though it reads as the same");
    }

    @Test
    @DisplayName("a negative figure is matched against the returned one")
    void matchesNegativeFigures() {
        assertTrue(NumberGuard.numbersAreSafe(
                "The month closed at ETB -150.25.", ALLOWED, Set.of()),
                "-150.25 is in the tool results");
        assertFalse(NumberGuard.numbersAreSafe(
                "The month closed at ETB -151.00.", ALLOWED, Set.of()),
                "-151.00 is not");
    }

    @Test
    @DisplayName("structural numbers a reader needs are accepted")
    void acceptsStructuralNumbers() {
        assertTrue(NumberGuard.numbersAreSafe(
                "Within 30 days, across 7 products.", ALLOWED, Set.of()),
                "day counts and list positions carry no business meaning");
        assertTrue(NumberGuard.numbersAreSafe(
                "The second product is 5 units.", ALLOWED, Set.of()),
                "a list index the answer itself is listing");
    }

    @Test
    @DisplayName("a date inside a period label is accepted")
    void acceptsDatesInLabels() {
        assertTrue(NumberGuard.numbersAreSafe(
                "For the month to 2026-10-14.", ALLOWED, Set.of("the month to 2026-10-14")),
                "the period label is part of the answer");
    }

    @Test
    @DisplayName("a year mentioned in passing is accepted")
    void acceptsYears() {
        assertTrue(NumberGuard.numbersAreSafe(
                "Recorded since 2026.", ALLOWED, Set.of()),
                "a year is a date part, not a business figure");
    }

    @Test
    @DisplayName("prose with no numbers at all is accepted")
    void acceptsNumberlessProse() {
        assertTrue(NumberGuard.numbersAreSafe(
                "I do not have that information.", ALLOWED, Set.of()),
                "there is nothing to check");
        assertTrue(NumberGuard.numbersAreSafe(null, ALLOWED, Set.of()),
                "nothing to check");
    }

    @Test
    @DisplayName("collect flattens tool results into the numbers they contain")
    void collectFlattensToolResults() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("netProfit", new BigDecimal("12400.00"));
        result.put("period", "the last 30 days");
        result.put("salesCount", 42);
        result.put("items", java.util.List.of(
                Map.of("name", "Birr", "amount", new BigDecimal("3200"))));

        Set<BigDecimal> collected = NumberGuard.collect(
                Map.of("profit", result));

        assertTrue(collected.contains(new BigDecimal("12400.00")));
        assertTrue(collected.contains(new BigDecimal("3200")));
        assertTrue(collected.contains(new BigDecimal("42")));
        // "30" inside the period label is collected too, which is why
        // labels are added to the allowed text by the caller.
        assertTrue(collected.contains(new BigDecimal("30")),
                "numbers inside text are collected, not ignored");
    }

    @Test
    @DisplayName("scrub removes the offending figures from the draft")
    void scrubMasksFigures() {
        String scrubbed = NumberGuard.scrub("Net profit was ETB 13,500.00.");
        assertFalse(scrubbed.contains("13,500"), "the invented figure is not left to read");
        assertTrue(scrubbed.contains("Net profit was"), "the sentence survives");
    }

    @Test
    @DisplayName("money formats a figure the way the deterministic reply writes it")
    void moneyFormatsWithSeparatorsAndDecimals() {
        assertEquals("12,400.00", NumberGuard.money(new BigDecimal("12400")));
        assertEquals("0.00", NumberGuard.money(BigDecimal.ZERO));
        assertEquals("0.00", NumberGuard.money(null));
    }
}
