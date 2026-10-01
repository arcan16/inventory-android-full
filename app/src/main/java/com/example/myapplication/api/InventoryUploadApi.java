package com.example.myapplication.api;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.domain.InventoryCsvParser;
import com.example.myapplication.domain.ProductPresentation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.myapplication.api.Queries.args;

/**
 * Puerto de InventoryUploadController + InventoryCsvLoaderService: a partir
 * del reporte de existencias (csv) del proveedor crea el inventario, da de
 * alta los productos nuevos (ignorando los que ya existen), registra el stock
 * y la presentacion de cada producto con su codigo de barras. Todo en una sola
 * transaccion: si algo falla no queda un inventario a medias.
 */
public class InventoryUploadApi {

    private final AppDatabase database;

    InventoryUploadApi(AppDatabase database) {
        this.database = database;
    }

    public Call<InventoryUploadResultDTO> upload(byte[] csvBytes) {
        return new Call<>(() -> {
            if (csvBytes.length == 0) {
                return Response.badRequest("El archivo esta vacio");
            }
            InventoryCsvParser.ParsedInventoryFile parsed;
            try {
                parsed = InventoryCsvParser.parse(csvBytes);
            } catch (IllegalArgumentException e) {
                return Response.badRequest(e.getMessage());
            }

            SQLiteDatabase db = database.getWritableDatabase();
            db.beginTransaction();
            try {
                InventoryUploadResultDTO result = load(db, parsed);
                db.setTransactionSuccessful();
                return Response.success(result);
            } finally {
                db.endTransaction();
            }
        });
    }

    private static InventoryUploadResultDTO load(SQLiteDatabase db, InventoryCsvParser.ParsedInventoryFile parsed) {
        ContentValues inventory = new ContentValues();
        inventory.put("inventory_date", Queries.today());
        inventory.put("presentation", parsed.presentation);
        inventory.put("status", Queries.STATUS_OPENED);
        long inventoryId = db.insertOrThrow("inventories", null, inventory);

        // Primera descripcion de cada producto en el archivo.
        Map<String, String> descriptionByProductId = new LinkedHashMap<>();
        for (InventoryCsvParser.ProductRow row : parsed.rows) {
            if (!descriptionByProductId.containsKey(row.productId)) {
                descriptionByProductId.put(row.productId, row.description);
            }
        }

        int productsCreated = 0;
        for (Map.Entry<String, String> product : descriptionByProductId.entrySet()) {
            ContentValues values = new ContentValues();
            values.put("id", product.getKey());
            values.put("description", product.getValue());
            if (db.insertWithOnConflict("products", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1) {
                productsCreated++;
            }
        }

        for (InventoryCsvParser.ProductRow row : parsed.rows) {
            ContentValues values = new ContentValues();
            values.put("id_product", row.productId);
            values.put("stock", row.stock);
            values.put("id_inventory", inventoryId);
            db.insertOrThrow("stock", null, values);
        }

        PresentationsSummary presentations = registerPresentations(db, parsed);

        return new InventoryUploadResultDTO(inventoryId, parsed.presentation, parsed.rows.size(), productsCreated,
                parsed.rows.size(), presentations.created, presentations.barcodesAssigned,
                presentations.skipped, presentations.barcodeConflicts);
    }

    private static final class PresentationsSummary {
        int created;
        int barcodesAssigned;
        int skipped;
        int barcodeConflicts;
    }

    /** Presentacion existente o por crear; id null hasta que se inserta. */
    private static final class PresentationRow {
        Long id;
        final String productId;
        final ProductPresentation presentation;
        String barcode;

        PresentationRow(Long id, String productId, ProductPresentation presentation, String barcode) {
            this.id = id;
            this.productId = productId;
            this.presentation = presentation;
            this.barcode = barcode;
        }
    }

    /**
     * Da de alta la presentacion (UNIDAD) de cada fila y le asigna el COD BARRAS
     * cuando el archivo lo trae:
     * - Si el producto aun no tiene esa presentacion, se crea.
     * - Si la presentacion no tiene codigo y la fila si, se le asigna. Un codigo
     *   ya guardado se conserva aunque el archivo traiga otro distinto.
     * - Si el codigo ya pertenece a otra presentacion no se asigna (barcode es
     *   UNIQUE) y se cuenta como conflicto en lugar de abortar la carga.
     * - Las filas con una UNIDAD desconocida se omiten aqui, pero su producto y
     *   su stock ya quedaron registrados.
     */
    private static PresentationsSummary registerPresentations(SQLiteDatabase db, InventoryCsvParser.ParsedInventoryFile parsed) {
        Map<String, PresentationRow> presentationByKey = new HashMap<>();
        Map<String, PresentationRow> ownerByBarcode = new HashMap<>();
        try (Cursor c = db.rawQuery("SELECT id, id_product, presentation, barcode FROM product_presentations", null)) {
            while (c.moveToNext()) {
                ProductPresentation presentation = ProductPresentation.findByLabel(c.getString(2));
                PresentationRow row = new PresentationRow(c.getLong(0), c.getString(1), presentation,
                        c.isNull(3) ? null : c.getString(3));
                if (presentation != null) {
                    presentationByKey.put(key(row.productId, presentation), row);
                }
                if (row.barcode != null) {
                    ownerByBarcode.put(row.barcode, row);
                }
            }
        }

        PresentationsSummary summary = new PresentationsSummary();
        for (InventoryCsvParser.ProductRow fileRow : parsed.rows) {
            ProductPresentation presentation = ProductPresentation.findByLabel(fileRow.unit);
            if (presentation == null) {
                summary.skipped++;
                continue;
            }

            String key = key(fileRow.productId, presentation);
            PresentationRow row = presentationByKey.get(key);
            if (row == null) {
                row = new PresentationRow(null, fileRow.productId, presentation, null);
                ContentValues values = new ContentValues();
                values.put("id_product", row.productId);
                values.put("presentation", presentation.getLabel());
                row.id = db.insertOrThrow("product_presentations", null, values);
                presentationByKey.put(key, row);
                summary.created++;
            }

            // Solo se completa el codigo de las presentaciones que aun no tienen uno.
            String barcode = fileRow.barcode;
            if (barcode == null || row.barcode != null) {
                continue;
            }
            if (ownerByBarcode.containsKey(barcode)) {
                summary.barcodeConflicts++;
                continue;
            }

            row.barcode = barcode;
            ContentValues values = new ContentValues();
            values.put("barcode", barcode);
            db.update("product_presentations", values, "id = ?", args(row.id));
            ownerByBarcode.put(barcode, row);
            summary.barcodesAssigned++;
        }
        return summary;
    }

    private static String key(String productId, ProductPresentation presentation) {
        return productId + "|" + presentation.name();
    }
}
