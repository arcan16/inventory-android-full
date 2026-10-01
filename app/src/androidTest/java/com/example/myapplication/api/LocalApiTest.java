package com.example.myapplication.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.myapplication.db.AppDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Recorre las operaciones que antes resolvia el backend contra una base de
 * datos en memoria (no toca los datos de la app). Requiere un dispositivo o
 * emulador: ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4.class)
public class LocalApiTest {

    private static final String CSV = "RAZON SOCIAL,,,\n"
            + "REPORTE DE EXISTENCIAS,,,\n"
            + ",,,FECHA FINAL:\n"
            + "SUCURSAL:,,(5) ANAHUAC,\n"
            + "PRODUCTO,UNIDAD,,EXISTENCIA,NO SHOW,,DESCRIPCION DEL PRODUCTO,COD BARRAS\n"
            + "100,4 LTS,,2.000, ,,PINTURA BLANCA,7500000000001\n"
            + "860/P5,4 LTS,,1.500, ,,ESMALTE ROJO,\n"
            + "200,GARRAFA,,3.000, ,,THINNER,\n"
            + ",,,,,,,\n"
            + "\"NESTOR, 04-ENE.-24\",,,,,,,\n";

    private AppDatabase database;
    private File reportsDir;
    private InventoriesApi inventories;
    private InventoryUploadApi upload;
    private ProductCountsApi counts;
    private ProductsApi products;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = AppDatabase.createInMemory(context);
        reportsDir = new File(context.getCacheDir(), "reports-test");
        inventories = new InventoriesApi(database);
        upload = new InventoryUploadApi(database);
        counts = new ProductCountsApi(database, reportsDir);
        products = new ProductsApi(database);
    }

    @After
    public void tearDown() {
        database.close();
        File[] files = reportsDir.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        reportsDir.delete();
    }

    @Test
    public void uploadCreatesInventoryProductsStockAndPresentations() throws Exception {
        InventoryUploadResultDTO result = uploadCsv();

        assertEquals("4 LTS", result.getPresentation());
        assertEquals(3, result.getRowsProcessed());
        assertEquals(3, result.getProductsCreated());
        assertEquals(3, result.getStockRowsCreated());
        assertEquals(2, result.getPresentationsCreated()); // "GARRAFA" no es una presentacion conocida.
        assertEquals(1, result.getBarcodesAssigned());
        assertEquals(1, result.getPresentationsSkipped());

        // La segunda carga no duplica productos ni presentaciones.
        InventoryUploadResultDTO second = uploadCsv();
        assertEquals(0, second.getProductsCreated());
        assertEquals(0, second.getPresentationsCreated());
        assertEquals(0, second.getBarcodeConflicts());

        PageResponse<InventoryDTO> page = ok(inventories.getInventories(0, 50));
        assertEquals(2, page.getTotalElements());
        assertEquals(second.getInventoryId(), page.getContent().get(0).getId()); // Mas reciente primero.
        assertEquals("OPENED", page.getContent().get(0).getStatus());

        ProductPresentationDTO byBarcode = ok(products.getPresentationByBarcode("7500000000001"));
        assertEquals("100", byBarcode.getProductId());
        assertEquals("PINTURA BLANCA", byBarcode.getDescription());
    }

    @Test
    public void summaryAddsCountsAndAppendsProductsWithoutStock() throws Exception {
        long id = uploadCsv().getInventoryId();

        // Sin conteos el resumen responde 400, como el backend.
        assertEquals(Response.BAD_REQUEST, counts.getSummary(id, 0, 20, false).execute().code());

        ok(counts.addProductCount(new NewProductCountRequest(id, "100", 0.1f, "SALES_AREA")));
        ok(counts.addProductCount(new NewProductCountRequest(id, "100", 0.2f, "WAREHOUSE")));
        ok(counts.addProductCount(new NewProductCountRequest(id, "860/P5", 1.5f, "SALES_AREA")));
        ok(counts.createProductAddCount(new CreateProductCountRequest(id, "999", "PRODUCTO NUEVO", 4f, "NOTE")));

        List<CountsDifferenceDTO> rows = ok(counts.getSummary(id, 0, 20, false)).getContent();
        assertEquals(4, rows.size());
        assertEquals("100", rows.get(0).getIdProduct());
        assertEquals(0.3, rows.get(0).getSum(), 0); // Redondeado a 3 decimales, sin 0.30000000000000004.
        assertEquals(-1.7, rows.get(0).getDifference(), 0);
        assertEquals("999", rows.get(3).getIdProduct());
        assertEquals(4.0, rows.get(3).getDifference(), 0);

        PageResponse<CountsDifferenceDTO> differences = ok(counts.getSummary(id, 0, 20, true));
        assertEquals(3, differences.getTotalElements()); // 860/P5 cuadra exacto.

        NormalInventoryDataDTO data = ok(inventories.getNormalInventoryData(id));
        assertEquals(4, data.getProductsCount().size());
        assertEquals(3, data.getStock().size());
        assertEquals(1, data.getBarcodes().size());

        // Duplicar un producto existente se rechaza.
        assertEquals(Response.BAD_REQUEST, counts.createProductAddCount(
                new CreateProductCountRequest(id, "999", "OTRO", 1f, "NOTE")).execute().code());
    }

    @Test
    public void reportPdfIsGenerated() throws Exception {
        long id = uploadCsv().getInventoryId();
        ok(counts.addProductCount(new NewProductCountRequest(id, "100", 1f, "SALES_AREA")));

        File pdf = ok(counts.downloadReport(id));

        assertTrue(pdf.length() > 0);
        byte[] header = new byte[4];
        try (InputStream input = new FileInputStream(pdf)) {
            assertEquals(4, input.read(header));
        }
        assertEquals("%PDF", new String(header, StandardCharsets.US_ASCII));
        assertEquals(1, ok(counts.getAllReports()).size());
    }

    @Test
    public void barcodesAreUniqueAcrossPresentations() throws Exception {
        uploadCsv();

        // 860/P5 en 1 LT aun no existe: se crea con el codigo.
        ProductPresentationDTO created = ok(products.assignBarcode("860/P5", new AssignBarcodeRequest("1 lt", "7500000000002")));
        assertEquals("1 LT", created.getPresentation());

        // Un codigo que ya es de otra presentacion se rechaza.
        Response<ProductPresentationDTO> conflict =
                products.assignBarcode("860/P5", new AssignBarcodeRequest("4 LTS", "7500000000001")).execute();
        assertEquals(Response.BAD_REQUEST, conflict.code());
        assertTrue(conflict.errorMessage(), conflict.errorMessage().contains("100 (4 LTS)"));

        List<ProductPresentationDTO> presentations = ok(products.getPresentations("860/P5"));
        assertEquals("1 LT", presentations.get(0).getPresentation()); // Menor volumen primero.
        assertEquals("4 LTS", presentations.get(1).getPresentation());

        // Quitar el codigo y cambiar la descripcion desde la presentacion.
        ProductPresentationDTO updated = ok(products.updatePresentation(created.getId(), "860/P5",
                new UpdateProductPresentationRequest("ESMALTE ROJO OXIDO", "", true)));
        assertEquals("ESMALTE ROJO OXIDO", updated.getDescription());
        assertEquals(null, updated.getBarcode());
        assertEquals(Response.NOT_FOUND, products.getPresentationByBarcode("7500000000002").execute().code());
    }

    @Test
    public void closeReopenAndDeleteInventory() throws Exception {
        long id = uploadCsv().getInventoryId();
        ok(counts.addProductCount(new NewProductCountRequest(id, "100", 1f, "SALES_AREA")));

        assertEquals("CLOSED", ok(inventories.closeInventory(id)).getStatus());
        assertEquals(Response.BAD_REQUEST, inventories.closeInventory(id).execute().code());
        assertEquals("OPENED", ok(inventories.reopenInventory(id)).getStatus());

        assertTrue(inventories.deleteInventory(id).execute().isSuccessful());
        assertFalse(ok(inventories.getInventories(0, 50)).getTotalElements() > 0);
        assertEquals(0, ok(counts.getAllReports()).size()); // Los conteos se borraron en cascada.
        assertEquals(Response.BAD_REQUEST, inventories.deleteInventory(id).execute().code());
    }

    @Test
    public void backupRoundTripMergesWithoutDeleting() throws Exception {
        long id = uploadCsv().getInventoryId();
        ok(counts.addProductCount(new NewProductCountRequest(id, "100", 1.2f, "SALES_AREA")));
        ok(inventories.closeInventory(id));
        byte[] backup = ok(new DataTransferApi(database).exportData());

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppDatabase target = AppDatabase.createInMemory(context);
        try {
            DataTransferApi targetTransfer = new DataTransferApi(target);
            ImportResultDTO first = ok(targetTransfer.importData(backup));
            assertEquals(3, first.getProductsCreated());
            assertEquals(2, first.getPresentationsCreated());
            assertEquals(1, first.getBarcodesAssigned());
            assertEquals(1, first.getInventoriesCreated());
            assertEquals(3, first.getStockRowsCreated());
            assertEquals(1, first.getCountsCreated());
            assertEquals(0, first.getRowsSkipped());

            InventoryDTO imported = ok(new InventoriesApi(target).getInventories(0, 50)).getContent().get(0);
            assertEquals("CLOSED", imported.getStatus());
            List<CountsDifferenceDTO> summary =
                    ok(new ProductCountsApi(target, reportsDir).getSummary(imported.getId(), 0, 20, false)).getContent();
            assertEquals(1.2, summary.get(0).getSum(), 0); // El float se exporta como "1.2".

            // Importar otra vez no duplica catalogo; los inventarios si se agregan.
            ImportResultDTO second = ok(targetTransfer.importData(backup));
            assertEquals(0, second.getProductsCreated());
            assertEquals(0, second.getPresentationsCreated());
            assertEquals(0, second.getBarcodesAssigned());
            assertEquals(1, second.getInventoriesCreated());
        } finally {
            target.close();
        }
    }

    @Test
    public void importAcceptsServerBackupAndSkipsInvalidRows() throws Exception {
        uploadCsv();
        String json = "{\"format\":\"comex-inventory-backup\",\"version\":1,"
                + "\"products\":[{\"id\":\"100\",\"description\":\"PINTURA BLANCA MATE\"},{\"id\":\"\",\"description\":\"X\"}],"
                + "\"productPresentations\":[{\"productId\":\"860/P5\",\"presentation\":\"4 LTS\",\"barcode\":\"7500000000001\"},"
                + "{\"productId\":\"999\",\"presentation\":\"1 LT\",\"barcode\":null}],"
                + "\"inventories\":[{\"id\":3,\"date\":\"2024-01-04\",\"presentation\":\"4 LTS\",\"status\":\"LOCKED\","
                + "\"stock\":[{\"productId\":\"100\",\"stock\":2}],"
                + "\"counts\":[{\"productId\":\"100\",\"quantity\":1,\"place\":\"COCINA\"}]},"
                + "{\"date\":\"04/01/2024\",\"presentation\":\"4 LTS\"}]}";

        ImportResultDTO result = ok(new DataTransferApi(database).importData(json.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, result.getProductsUpdated());
        assertEquals(1, result.getBarcodeConflicts()); // 7500000000001 ya es de 100 (4 LTS).
        assertEquals(1, result.getInventoriesCreated());
        // Producto sin id, presentacion de 999, lugar COCINA e inventario con fecha invalida.
        assertEquals(4, result.getRowsSkipped());
        InventoryDTO imported = null;
        for (InventoryDTO inventory : ok(inventories.getInventories(0, 50)).getContent()) {
            if ("2024-01-04".equals(inventory.getDate())) {
                imported = inventory;
            }
        }
        assertNotNull(imported);
        assertEquals("OPENED", imported.getStatus()); // LOCKED no se importa.

        Response<ImportResultDTO> invalid = new DataTransferApi(database)
                .importData("{\"format\":\"otro\"}".getBytes(StandardCharsets.UTF_8)).execute();
        assertEquals(Response.BAD_REQUEST, invalid.code());
    }

    private InventoryUploadResultDTO uploadCsv() throws Exception {
        return ok(upload.upload(CSV.getBytes(StandardCharsets.UTF_8)));
    }

    private static <T> T ok(Call<T> call) throws Exception {
        Response<T> response = call.execute();
        assertTrue(response.errorMessage(), response.isSuccessful());
        assertNotNull(response.body());
        return response.body();
    }
}
