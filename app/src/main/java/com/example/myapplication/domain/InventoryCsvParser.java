package com.example.myapplication.domain;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Puerto del InventoryCsvParser del backend (que a su vez porto
 * CargaDeArchivos/modules/converter.py): limpia el reporte de existencias que
 * entrega el proveedor. El archivo trae 4 filas de encabezado/metadatos antes
 * del encabezado real (fila 5), varias columnas vacias intercaladas, una
 * columna "NO SHOW" que no se usa, y 2 filas de pie de reporte al final. Los
 * reportes mas recientes agregan la columna opcional "COD BARRAS".
 *
 * Errores de formato: IllegalArgumentException con un mensaje para el usuario.
 */
public final class InventoryCsvParser {

    private static final String HEADER_PRODUCT = "PRODUCTO";
    private static final String HEADER_UNIT = "UNIDAD";
    private static final String HEADER_STOCK = "EXISTENCIA";
    private static final String HEADER_DESCRIPTION = "DESCRIPCION DEL PRODUCTO";
    private static final String HEADER_BARCODE = "COD BARRAS";

    // Indice (0-based) de la fila que trae el encabezado real dentro del archivo (fila 5 del csv).
    private static final int HEADER_ROW_INDEX = 4;
    // A partir de que fila (0-based) comienzan los datos (fila 6 del csv).
    private static final int DATA_START_INDEX = 5;
    // Filas de pie de reporte al final del archivo que deben descartarse.
    private static final int TRAILING_ROWS_TO_DISCARD = 2;

    // El sistema del proveedor exporta en Windows-1252 (p. ej. la Ñ es el byte 0xD1).
    private static final Charset FALLBACK_CHARSET = Charset.forName("windows-1252");

    /** barcode es null cuando el archivo no trae la columna o la celda viene vacia. */
    public static final class ProductRow {
        public final String productId;
        public final String unit;
        public final String description;
        public final float stock;
        public final String barcode;

        ProductRow(String productId, String unit, String description, float stock, String barcode) {
            this.productId = productId;
            this.unit = unit;
            this.description = description;
            this.stock = stock;
            this.barcode = barcode;
        }
    }

    public static final class ParsedInventoryFile {
        public final String presentation;
        public final List<ProductRow> rows;

        ParsedInventoryFile(String presentation, List<ProductRow> rows) {
            this.presentation = presentation;
            this.rows = rows;
        }
    }

    private InventoryCsvParser() {
    }

    public static ParsedInventoryFile parse(byte[] bytes) {
        List<List<String>> allLines = CsvReader.parse(decode(bytes));

        if (allLines.size() <= DATA_START_INDEX) {
            throw new IllegalArgumentException("El archivo no tiene el formato esperado del reporte de existencias");
        }

        Map<String, Integer> columnIndexByName = resolveColumns(allLines.get(HEADER_ROW_INDEX));

        List<List<String>> dataRows = allLines.subList(DATA_START_INDEX, allLines.size());
        if (dataRows.size() <= TRAILING_ROWS_TO_DISCARD) {
            throw new IllegalArgumentException("El archivo no contiene productos para cargar");
        }
        dataRows = dataRows.subList(0, dataRows.size() - TRAILING_ROWS_TO_DISCARD);

        int productIdx = columnIndexByName.get(HEADER_PRODUCT);
        int unitIdx = columnIndexByName.get(HEADER_UNIT);
        int stockIdx = columnIndexByName.get(HEADER_STOCK);
        int descriptionIdx = columnIndexByName.get(HEADER_DESCRIPTION);
        Integer barcodeIdx = columnIndexByName.get(HEADER_BARCODE);

        List<ProductRow> rows = new ArrayList<>();
        for (int i = 0; i < dataRows.size(); i++) {
            List<String> record = dataRows.get(i);
            // Numero de registro 1-based dentro del archivo, como CSVRecord.getRecordNumber().
            long recordNumber = DATA_START_INDEX + i + 1L;

            String productId = cell(record, productIdx, recordNumber);
            if (productId.isEmpty()) {
                throw new IllegalArgumentException("La fila " + recordNumber + " del archivo no tiene un id de producto valido");
            }

            String stockRaw = cell(record, stockIdx, recordNumber);
            float stock;
            try {
                stock = Float.parseFloat(stockRaw);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("La fila " + recordNumber + " tiene un valor de existencia invalido: '" + stockRaw + "'");
            }

            String unit = cell(record, unitIdx, recordNumber);
            String description = cell(record, descriptionIdx, recordNumber);
            String barcode = barcodeIdx != null ? cell(record, barcodeIdx, recordNumber) : "";
            rows.add(new ProductRow(productId, unit, description, stock, barcode.isEmpty() ? null : barcode));
        }

        String presentation = rows.get(0).unit;
        return new ParsedInventoryFile(presentation, rows);
    }

    /** Celda recortada; una fila mas corta que el encabezado es un error de formato (como en commons-csv). */
    private static String cell(List<String> record, int index, long recordNumber) {
        if (index >= record.size()) {
            throw new IllegalArgumentException("La fila " + recordNumber + " del archivo tiene menos columnas que el encabezado");
        }
        return record.get(index).trim();
    }

    /** Lee el archivo como UTF-8 si es valido; si no, como Windows-1252. */
    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, FALLBACK_CHARSET);
        }
    }

    /**
     * Ubica cada columna por su nombre en el encabezado (primera aparicion),
     * ignorando las columnas sin nombre y las que no se usan ("NO SHOW",
     * "P UNIT", "IMPORTE"). "COD BARRAS" es opcional para seguir aceptando
     * los reportes anteriores que no la traen.
     */
    private static Map<String, Integer> resolveColumns(List<String> headerRow) {
        Map<String, Integer> columnIndexByName = new HashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String name = headerRow.get(i).trim();
            if (!name.isEmpty() && !columnIndexByName.containsKey(name)) {
                columnIndexByName.put(name, i);
            }
        }

        for (String required : Arrays.asList(HEADER_PRODUCT, HEADER_UNIT, HEADER_STOCK, HEADER_DESCRIPTION)) {
            if (!columnIndexByName.containsKey(required)) {
                throw new IllegalArgumentException("El archivo no contiene la columna esperada '" + required + "'");
            }
        }

        return columnIndexByName;
    }
}
