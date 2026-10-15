package com.inventory.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * CSV and PDF rendering for reports.
 *
 * <p>Both are rendered from the same {@code Map<String, Object>} a report
 * endpoint returns, so an export can never disagree with the screen it was
 * downloaded from. There is no second query and no second set of arithmetic.
 *
 * <p>CSV is written here rather than pulled in as a library. RFC 4180 is a
 * quoting rule and a line ending, and getting it subtly wrong is how a product
 * name containing a comma ends up as an extra spreadsheet column — a small
 * amount of code that can be read and tested against the specification is
 * better here than a dependency.
 */
@Service
public class ExportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Most rows a single export may contain.
     *
     * <p>A report over a wide date range can return every movement in the
     * business. Both renderers build the whole file in memory on the request
     * thread, so an unbounded export is a way to exhaust the heap with one call.
     *
     * <p>Configurable because the right number depends on the deployment and on
     * the report: a thousand rows of a movement history is a routine download,
     * while a thousand rows is already too many to read in a PDF.
     */
    @org.springframework.beans.factory.annotation.Value(
            "${app.report.export-max-rows:10000}")
    private int maxRows = 10000;

    /** The configured cap, for the controller to enforce before rendering. */
    public int getMaxRows() {
        return maxRows;
    }

    /**
     * Renders a report as CSV.
     *
     * <p>RFC 4180: fields containing a comma, a quote or a line break are wrapped
     * in double quotes and internal quotes are doubled; rows end with CRLF. A
     * cell beginning with {@code =}, {@code +}, {@code -} or {@code @} is
     * prefixed with a quote as well, because spreadsheet software treats those
     * as a formula — a product named {@code =SUM(A1:A9)} would otherwise execute
     * when the file is opened.
     */
    public byte[] toCsv(String reportName, LocalDateTime generatedAt,
                        List<String> columns, List<Map<String, Object>> rows) {
        StringBuilder csv = new StringBuilder();
        csv.append(escape(reportName)).append('\r');
        csv.append("Generated ").append(escape(generatedAt.format(STAMP))).append('\r');
        csv.append('\r');

        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escape(columns.get(i)));
        }
        csv.append("\r\n");

        for (Map<String, Object> row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    csv.append(',');
                }
                csv.append(escape(format(row.get(columns.get(i)))));
            }
            csv.append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Renders a report as PDF.
     *
     * <p>Landscape, with the table header repeated on every page — a report read
     * on page three has to say what its columns mean without the reader having to
     * go back to page one.
     */
    public byte[] toPdf(String reportName, LocalDateTime generatedAt,
                        List<String> columns, List<Map<String, Object>> rows) {
        Document document = new Document(PageSize.A4.rotate(), 28, 28, 40, 30);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8);

            Paragraph title = new Paragraph(reportName, titleFont);
            title.setSpacingAfter(2);
            document.add(title);
            document.add(new Paragraph(
                    "StockFlow — generated " + generatedAt.format(STAMP), metaFont));
            document.add(new Paragraph(
                    generatedAt + " — figures computed from recorded transactions; "
                            + "see the profit and loss report for the accounting basis.",
                    metaFont));
            document.add(Paragraph.getInstance("\n"));

            PdfPTable table = new PdfPTable(columns.size());
            table.setWidthPercentage(100);
            table.setWidths(weights(columns));

            for (String column : columns) {
                PdfPCell cell = new PdfPCell(new Phrase(column, headFont));
                cell.setBackgroundColor(new java.awt.Color(238, 240, 243));
                cell.setPadding(3);
                table.addCell(cell);
            }
            table.setHeaderRows(1);

            for (Map<String, Object> row : rows) {
                for (String column : columns) {
                    PdfPCell cell = new PdfPCell(
                            new Phrase(format(row.get(column)), bodyFont));
                    cell.setPadding(3);
                    table.addCell(cell);
                }
            }

            if (rows.isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase(
                        "No records in this period.", bodyFont));
                empty.setColspan(columns.size());
                empty.setPadding(6);
                table.addCell(empty);
            }

            document.add(table);
            document.close();
        } catch (Exception e) {
            throw new IllegalStateException("Could not render the PDF export: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    /** Wider columns for text fields, narrow ones for numbers. */
    private static float[] weights(List<String> columns) {
        float[] weights = new float[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            String name = columns.get(i).toLowerCase(java.util.Locale.ROOT);
            weights[i] = (name.contains("description") || name.contains("reason")
                    || name.contains("product") || name.contains("customer")
                    || name.contains("supplier") || name.contains("note")) ? 3f : 1.4f;
        }
        return weights;
    }

    /** RFC 4180 field escaping, plus spreadsheet-formula neutralisation. */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String safe = value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            // Prefixing with a quote stops Excel and LibreOffice treating the
            // cell as a formula. Without it, a supplier named "=cmd|..." is a
            // code-execution risk the moment the export is opened.
            safe = "'" + safe;
        }
        boolean needsQuoting = safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0;
        if (!needsQuoting) {
            return safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    /**
     * Turns a report cell into text.
     *
     * <p>BigDecimal keeps its scale rather than being reformatted, so an export
     * of 10.00 does not silently become 10 and a column of money stops adding
     * up to its own total.
     */
    private static String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Double d) {
            return d == Math.floor(d) && !d.isInfinite()
                    ? String.valueOf(d.longValue()) : String.valueOf(d);
        }
        if (value instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object item : list) {
                parts.add(format(item));
            }
            return String.join("; ", parts);
        }
        return String.valueOf(value);
    }
}