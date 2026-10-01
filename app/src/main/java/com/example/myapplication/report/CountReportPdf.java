package com.example.myapplication.report;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import com.example.myapplication.api.CountsDifferenceDTO;
import com.example.myapplication.api.InventoryDTO;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Reporte PDF del conteo fisico; mismo contenido que generaba PdfCreator (iText)
 * en el backend: titulo, datos del inventario, tabla de productos con
 * diferencia y el balance del conteo. Se dibuja con PdfDocument del framework,
 * por lo que no requiere dependencias. La tabla continua en paginas nuevas
 * repitiendo su encabezado.
 */
public final class CountReportPdf {

    // A4 en puntos y margenes (izq, der, arriba, abajo) iguales a los del backend.
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final float MARGIN_LEFT = 35f;
    private static final float MARGIN_RIGHT = 30f;
    private static final float MARGIN_TOP = 50f;
    private static final float MARGIN_BOTTOM = 50f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT;

    private static final float[] COLUMN_WEIGHTS = {1f, 3f, 1f, 1f, 1f};
    private static final String[] COLUMN_TITLES = {"Id", "Descripcion", "Stock", "Fisico", "Diferencia"};
    private static final Paint.Align[] HEADER_ALIGN = {
            Paint.Align.CENTER, Paint.Align.CENTER, Paint.Align.CENTER, Paint.Align.CENTER, Paint.Align.CENTER};
    private static final Paint.Align[] DATA_ALIGN = {
            Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.RIGHT, Paint.Align.RIGHT};
    private static final float CELL_PADDING = 4f;
    private static final float HEADER_PADDING = 6f;
    private static final int HEADER_BACKGROUND = Color.rgb(230, 230, 230);

    private final PdfDocument document = new PdfDocument();
    private final Paint titlePaint = textPaint(Typeface.SERIF, Typeface.NORMAL, 16f);
    private final Paint paragraphPaint = textPaint(Typeface.SANS_SERIF, Typeface.NORMAL, 12f);
    private final Paint tableHeaderPaint = textPaint(Typeface.SANS_SERIF, Typeface.BOLD, 11f);
    private final Paint sectionTitlePaint = textPaint(Typeface.SANS_SERIF, Typeface.BOLD, 13f);
    private final Paint boldPaint = textPaint(Typeface.SANS_SERIF, Typeface.BOLD, 12f);
    private final Paint borderPaint = new Paint();
    private final Paint fillPaint = new Paint();

    private PdfDocument.Page page;
    private Canvas canvas;
    private int pageNumber;
    private float y;

    private CountReportPdf() {
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(0.5f);
        borderPaint.setColor(Color.BLACK);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(HEADER_BACKGROUND);
    }

    public static void write(File file, long idInventory, InventoryDTO inventory, List<CountsDifferenceDTO> report)
            throws IOException {
        CountReportPdf pdf = new CountReportPdf();
        try {
            pdf.newPage();
            pdf.addTitle("Reporte de Inventario Comex Anahuac");
            pdf.addLineBreaks();
            pdf.addReportMetadata(idInventory, inventory);
            pdf.addLineBreaks();
            pdf.addParagraph("Resumen del conteo del inventario fisico");
            pdf.addLineBreaks();
            pdf.addCountTable(report);
            pdf.addBalanceSummary(CountBalance.of(report));
            pdf.document.finishPage(pdf.page);

            try (OutputStream output = new FileOutputStream(file)) {
                pdf.document.writeTo(output);
            }
        } finally {
            pdf.document.close();
        }
    }

    private void newPage() {
        if (page != null) {
            document.finishPage(page);
        }
        pageNumber++;
        page = document.startPage(new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create());
        canvas = page.getCanvas();
        y = MARGIN_TOP;
    }

    /** Si "height" ya no cabe en la pagina actual, empieza otra. true si cambio de pagina. */
    private boolean ensureSpace(float height) {
        if (y + height > PAGE_HEIGHT - MARGIN_BOTTOM) {
            newPage();
            return true;
        }
        return false;
    }

    private void addTitle(String title) {
        List<String> lines = wrap(title, titlePaint, CONTENT_WIDTH);
        for (String line : lines) {
            float lineHeight = lineHeight(titlePaint);
            ensureSpace(lineHeight);
            float x = MARGIN_LEFT + (CONTENT_WIDTH - titlePaint.measureText(line)) / 2f;
            drawLine(line, x, titlePaint);
            y += lineHeight;
        }
    }

    private void addParagraph(String text) {
        for (String line : wrap(text, paragraphPaint, CONTENT_WIDTH)) {
            float lineHeight = lineHeight(paragraphPaint);
            ensureSpace(lineHeight);
            drawLine(line, MARGIN_LEFT, paragraphPaint);
            y += lineHeight;
        }
    }

    private void addLineBreaks() {
        y += 2 * lineHeight(paragraphPaint);
    }

    /** Datos generales del reporte: inventario al que pertenece y fecha en la que se genero. */
    private void addReportMetadata(long idInventory, InventoryDTO inventory) {
        String inventoryLine = "Inventario: #" + idInventory;
        if (inventory != null && inventory.getPresentation() != null) {
            inventoryLine += " - " + inventory.getPresentation();
        }
        addParagraph(inventoryLine);

        String inventoryDate = inventory != null ? formatInventoryDate(inventory.getDate()) : null;
        if (inventoryDate != null) {
            addParagraph("Fecha del inventario: " + inventoryDate);
        }

        addParagraph("Fecha de generacion: " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date()));
    }

    /** Solo los productos con diferencia (igual que el reporte del backend). */
    private void addCountTable(List<CountsDifferenceDTO> report) {
        float[] widths = columnWidths(CONTENT_WIDTH);
        drawTableHeader(widths);

        for (CountsDifferenceDTO row : report) {
            if (row.getDifference() == 0) {
                continue;
            }
            String[] cells = {
                    String.valueOf(row.getIdProduct()),
                    String.valueOf(row.getDescription()),
                    formatNumber(row.getStock()),
                    formatNumber(row.getSum()),
                    formatNumber(row.getDifference())
            };
            drawRow(cells, DATA_ALIGN, widths, paragraphPaint, CELL_PADDING, false);
        }
    }

    private void drawTableHeader(float[] widths) {
        drawRow(COLUMN_TITLES, HEADER_ALIGN, widths, tableHeaderPaint, HEADER_PADDING, true);
    }

    /** Un renglon de la tabla; si no cabe, sigue en otra pagina repitiendo el encabezado. */
    private void drawRow(String[] cells, Paint.Align[] align, float[] widths, Paint paint, float padding, boolean header) {
        List<List<String>> wrapped = new ArrayList<>();
        int maxLines = 1;
        for (int i = 0; i < cells.length; i++) {
            List<String> lines = wrap(cells[i], paint, widths[i] - 2 * padding);
            wrapped.add(lines);
            maxLines = Math.max(maxLines, lines.size());
        }
        float lineHeight = lineHeight(paint);
        float rowHeight = maxLines * lineHeight + 2 * padding;

        if (ensureSpace(rowHeight) && !header) {
            drawTableHeader(widths);
        }

        float x = MARGIN_LEFT;
        for (int i = 0; i < cells.length; i++) {
            if (header) {
                canvas.drawRect(x, y, x + widths[i], y + rowHeight, fillPaint);
            }
            canvas.drawRect(x, y, x + widths[i], y + rowHeight, borderPaint);

            float lineY = y + padding;
            for (String line : wrapped.get(i)) {
                float textWidth = paint.measureText(line);
                float textX;
                if (align[i] == Paint.Align.CENTER) {
                    textX = x + (widths[i] - textWidth) / 2f;
                } else if (align[i] == Paint.Align.RIGHT) {
                    textX = x + widths[i] - padding - textWidth;
                } else {
                    textX = x + padding;
                }
                drawText(line, textX, lineY, paint);
                lineY += lineHeight;
            }
            x += widths[i];
        }
        y += rowHeight;
    }

    private void addBalanceSummary(CountBalance balance) {
        addLineBreaks();

        float tableWidth = CONTENT_WIDTH * 0.6f;
        float sectionHeight = lineHeight(sectionTitlePaint) + 6f + 4 * (lineHeight(boldPaint) + 4f);
        ensureSpace(sectionHeight);

        drawLine("Balance del conteo fisico", MARGIN_LEFT, sectionTitlePaint);
        y += lineHeight(sectionTitlePaint) + 6f;

        double net = balance.netBalance();
        addSummaryRow(tableWidth, "Productos con sobrante",
                balance.productsWithSurplus + "  (+" + formatNumber(balance.totalSurplus) + ")");
        addSummaryRow(tableWidth, "Productos con faltante",
                balance.productsWithShortage + "  (-" + formatNumber(balance.totalShortage) + ")");
        addSummaryRow(tableWidth, "Productos exactos", String.valueOf(balance.exactProducts));
        addSummaryRow(tableWidth, "Balance neto", (net >= 0 ? "+" : "") + formatNumber(net));
    }

    private void addSummaryRow(float tableWidth, String label, String value) {
        float lineHeight = Math.max(lineHeight(paragraphPaint), lineHeight(boldPaint));
        drawLine(label, MARGIN_LEFT, paragraphPaint);
        drawLine(value, MARGIN_LEFT + tableWidth - boldPaint.measureText(value), boldPaint);
        y += lineHeight + 4f;
    }

    /** Dibuja una linea de texto con su parte superior en la posicion actual. */
    private void drawLine(String text, float x, Paint paint) {
        drawText(text, x, y, paint);
    }

    private void drawText(String text, float x, float top, Paint paint) {
        canvas.drawText(text, x, top - paint.ascent(), paint);
    }

    /** Parte el texto en lineas que caben en "width", cortando por palabras (o por letras si una palabra no cabe). */
    private static List<String> wrap(String text, Paint paint, float width) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (paint.measureText(candidate) <= width) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
            }
            String rest = word;
            while (paint.measureText(rest) > width && rest.length() > 1) {
                int fit = Math.max(1, paint.breakText(rest, true, width, null));
                lines.add(rest.substring(0, fit));
                rest = rest.substring(fit);
            }
            current.append(rest);
        }
        lines.add(current.toString());
        return lines;
    }

    private static float[] columnWidths(float totalWidth) {
        float totalWeight = 0;
        for (float weight : COLUMN_WEIGHTS) {
            totalWeight += weight;
        }
        float[] widths = new float[COLUMN_WEIGHTS.length];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = totalWidth * COLUMN_WEIGHTS[i] / totalWeight;
        }
        return widths;
    }

    private static float lineHeight(Paint paint) {
        Paint.FontMetrics metrics = paint.getFontMetrics();
        return metrics.descent - metrics.ascent + 2f;
    }

    private static Paint textPaint(Typeface family, int style, float size) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create(family, style));
        paint.setTextSize(size);
        paint.setColor(Color.BLACK);
        return paint;
    }

    /** "2024-01-04" -> "04/01/2024"; null si no se puede leer. */
    private static String formatInventoryDate(String isoDate) {
        if (isoDate == null) {
            return null;
        }
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate);
            return date != null ? new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(date) : null;
        } catch (ParseException e) {
            return null;
        }
    }

    private static String formatNumber(double value) {
        return String.format(Locale.US, "%.3f", value);
    }
}
