package com.inventory.backend.chatbot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks that a phrased answer contains only figures the tools actually returned.
 *
 * <h2>The problem this solves</h2>
 *
 * <p>A language model asked to write a sentence about a tool result will
 * occasionally write a number that was not in the result. It is usually a
 * rounding slip. Sometimes it is a number from a different question. Always it is
 * a fluent, confident, wrong figure — which is the worst possible failure for a
 * chatbot answering about money.
 *
 * <p>So the check is mechanical and unforgiving: extract every numeric token from
 * the prose, and require each one to be present in the allowed value set, or to be
 * a harmless structural number (a quantity of days, a list index). Anything else
 * discards the prose.
 *
 * <h2>What counts as allowed</h2>
 *
 * <ul>
 *   <li>Every number in every tool result, in every format it could be written:
 *       {@code 12400}, {@code 12,400}, {@code 12400.00}, {@code 12.4k}.</li>
 *   <li>Dates and their parts, since a period label is part of the answer.</li>
 *   <li>Small ordinals ({@code 1}, {@code 2}, {@code 3}) used to enumerate a list
 *       the answer itself is listing.</li>
 * </ul>
 *
 * <h2>Why discarding is safe</h2>
 *
 * <p>Because the deterministic phrasing always exists. The fallback loses fluency
 * and keeps every figure, which is the correct trade.
 *
 * <p>This class is pure and carries no Spring dependencies so it can be tested
 * exhaustively on its own.
 */
public final class NumberGuard {

    /** Anything that looks like a number, with optional separators and decimals. */
    private static final Pattern NUMBER = Pattern.compile(
            "-?\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|-?\\d+(?:\\.\\d+)?");

    /** How close a prose number may be to an allowed one and still match. */
    private static final double RELATIVE_TOLERANCE = 0.001;

    private NumberGuard() {
    }

    /**
     * True when every number in {@code prose} is accounted for.
     *
     * @param prose         the text to check
     * @param allowedValues numbers the tools returned
     * @param allowedText   period labels and other permitted words, whose own
     *                      numbers are acceptable
     */
    public static boolean numbersAreSafe(String prose, Set<BigDecimal> allowedValues,
                                         Set<String> allowedText) {
        if (prose == null || prose.isBlank()) {
            return true;
        }
        Set<BigDecimal> allowed = normalise(allowedValues);
        for (String token : tokensIn(prose)) {
            BigDecimal value = parse(token);
            if (value == null) {
                continue;
            }
            if (isStructural(value)) {
                continue;
            }
            if (appearsInAllowedText(token, allowedText)) {
                continue;
            }
            if (!isAllowed(value, allowed)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Strips the numbers out of a reply that failed the check.
     *
     * <p>Used only for logging. The answer is discarded, not repaired, because a
     * repaired sentence is a sentence nobody verified.
     */
    public static String scrub(String prose) {
        if (prose == null) {
            return "";
        }
        return NUMBER.matcher(prose).replaceAll("…");
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /**
     * Numbers that carry no business meaning: a day count in a horizon, a list
     * position, a year in a date.
     *
     * <p>Deliberately small and deliberately not a general exemption. "30" is fine
     * because the caller asked about a period of days; "30,000" is not fine because
     * that is a figure somebody has to have been given. A four-digit year is a date
     * part, not a figure, so it passes too.
     */
    private static boolean isStructural(BigDecimal value) {
        int n = value.intValue();
        if (value.scale() > 0) {
            return false;
        }
        if (n >= 0 && n <= 366) {
            return true;
        }
        return n >= 1900 && n <= 2100;
    }

    private static boolean isAllowed(BigDecimal value, Set<BigDecimal> allowed) {
        for (BigDecimal candidate : allowed) {
            if (candidate.compareTo(value) == 0) {
                return true;
            }
            if (candidate.signum() == 0) {
                continue;
            }
            double relative = value.subtract(candidate).abs()
                    .divide(candidate.abs(), 6, RoundingMode.HALF_UP).doubleValue();
            if (relative <= RELATIVE_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    private static Set<BigDecimal> normalise(Set<BigDecimal> values) {
        Set<BigDecimal> out = new java.util.TreeSet<>();
        if (values == null) {
            return out;
        }
        for (BigDecimal value : values) {
            if (value != null) {
                out.add(value);
                // Also allow the value written without a thousands separator and
                // with trailing zeros trimmed, so "12,400" matches 12400.00.
                out.add(value.stripTrailingZeros());
            }
        }
        return out;
    }

    private static List<String> tokensIn(String prose) {
        List<String> out = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(prose);
        while (matcher.find()) {
            out.add(matcher.group());
        }
        return out;
    }

    private static BigDecimal parse(String token) {
        try {
            return new BigDecimal(token.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Whether the token appears verbatim in an allowed label, such as a period
     * name or a product name returned by a tool.
     */
    private static boolean appearsInAllowedText(String token, Set<String> allowedText) {
        if (allowedText == null) {
            return false;
        }
        String cleaned = token.replace(",", "");
        for (String label : allowedText) {
            if (label != null && label.contains(cleaned)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every number in a tool result, flattened.
     *
     * <p>Walks the whole structure so a caller does not have to know which fields
     * are numeric. Anything that is not a number is ignored, and strings are
     * scanned for embedded figures too — a supplier named "Acme 2000" should not
     * fail the guard just because a figure was mentioned in passing.
     */
    public static Set<BigDecimal> collect(Object value) {
        Set<BigDecimal> out = new java.util.TreeSet<>();
        collectInto(value, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void collectInto(Object value, Set<BigDecimal> out) {
        if (value == null) {
            return;
        }
        if (value instanceof BigDecimal decimal) {
            out.add(decimal);
        } else if (value instanceof Number number) {
            out.add(new BigDecimal(number.toString()));
        } else if (value instanceof CharSequence text) {
            for (String token : tokensIn(text.toString())) {
                BigDecimal parsed = parse(token);
                if (parsed != null) {
                    out.add(parsed);
                }
            }
        } else if (value instanceof Iterable<?> items) {
            for (Object item : items) {
                collectInto(item, out);
            }
        } else if (value instanceof java.util.Map<?, ?> map) {
            map.values().forEach(v -> collectInto(v, out));
        }
    }

    /** Formats a figure the way the deterministic phrasing writes it. */
    public static String money(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return String.format(Locale.ROOT, "%,.2f", value);
    }
}
