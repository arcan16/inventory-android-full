package com.example.myapplication.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class InventoryCsvParserTest {

    private static final String HEADER = "RAZON SOCIAL,,,\n"
            + "REPORTE DE EXISTENCIAS,,,\n"
            + ",,,FECHA FINAL:\n"
            + "SUCURSAL:,,(5) ANAHUAC,\n"
            + "PRODUCTO,UNIDAD,,EXISTENCIA,NO SHOW,,DESCRIPCION DEL PRODUCTO,COD BARRAS\n";
    private static final String FOOTER = ",,,,,,,\n\"NESTOR, 04-ENE.-24\",,,,,,,\n";

    @Test
    public void parsesRealProviderReport() throws IOException {
        InventoryCsvParser.ParsedInventoryFile parsed = InventoryCsvParser.parse(readResource("GALONES.csv"));

        assertEquals("4 LTS", parsed.presentation);
        assertEquals(333, parsed.rows.size());

        InventoryCsvParser.ProductRow first = parsed.rows.get(0);
        assertEquals("10292", first.productId);
        assertEquals("4 LTS", first.unit);
        assertEquals("THINNER E", first.description);
        assertEquals(1.2f, first.stock, 0.0001f);
        assertNull(first.barcode); // Este reporte es anterior a la columna COD BARRAS.

        InventoryCsvParser.ProductRow last = parsed.rows.get(parsed.rows.size() - 1);
        assertEquals("HP-10-YEL", last.productId);
        assertEquals("YELLOW TINT", last.description);
    }

    @Test
    public void readsBarcodeColumnAndQuotedFields() {
        String csv = HEADER
                + "860/P5,1 LT,,2.500, ,,\"PINTURA, BLANCA\",7501234567890\n"
                + "120,1 LT,,1.000, ,,COMEX 100 ALUMINIO,\n"
                + FOOTER;

        InventoryCsvParser.ParsedInventoryFile parsed = InventoryCsvParser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertEquals(2, parsed.rows.size());
        assertEquals("860/P5", parsed.rows.get(0).productId);
        assertEquals("PINTURA, BLANCA", parsed.rows.get(0).description);
        assertEquals("7501234567890", parsed.rows.get(0).barcode);
        assertNull(parsed.rows.get(1).barcode);
    }

    @Test
    public void decodesWindows1252WhenNotUtf8() {
        String csv = HEADER + "200,PIEZA,,3, ,,BROCHA PEQUEÑA,\n" + FOOTER;

        InventoryCsvParser.ParsedInventoryFile parsed =
                InventoryCsvParser.parse(csv.getBytes(Charset.forName("windows-1252")));

        assertEquals("BROCHA PEQUEÑA", parsed.rows.get(0).description);
    }

    @Test
    public void rejectsMissingRequiredColumn() {
        String csv = HEADER.replace("EXISTENCIA", "CANTIDAD") + "200,PIEZA,,3, ,,BROCHA,\n" + FOOTER;

        assertRejected(csv, "EXISTENCIA");
    }

    @Test
    public void rejectsInvalidStock() {
        String csv = HEADER + "200,PIEZA,,tres, ,,BROCHA,\n" + FOOTER;

        assertRejected(csv, "La fila 6 tiene un valor de existencia invalido");
    }

    @Test
    public void rejectsFileWithoutProducts() {
        assertRejected(HEADER + FOOTER, "no contiene productos");
    }

    private static void assertRejected(String csv, String expectedMessagePart) {
        try {
            InventoryCsvParser.parse(csv.getBytes(StandardCharsets.UTF_8));
            fail("Se esperaba IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(expectedMessagePart));
        }
    }

    private static byte[] readResource(String name) throws IOException {
        try (InputStream input = InventoryCsvParserTest.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("No existe el recurso " + name);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }
}
