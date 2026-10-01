package com.example.myapplication.api;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.JsonWriter;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.domain.ProductPresentation;
import com.example.myapplication.util.BackupFileInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.example.myapplication.api.Queries.args;

/**
 * Puerto de DataTransferController/DataTransferService del backend: exporta e
 * importa el archivo de respaldo "comex-inventory-backup" (el mismo que usa la
 * app cliente contra el servidor, asi que sirve para pasar datos entre ambas).
 *
 * Importar combina con lo que ya existe y nunca borra:
 * - Productos: se crean los que faltan; en los existentes se actualiza la descripcion.
 * - Presentaciones: se crean las que faltan; el codigo de barras del archivo
 *   reemplaza al guardado, salvo que ya pertenezca a otra presentacion (conflicto).
 * - Inventarios: siempre se crean nuevos, con su stock y sus conteos.
 * Todo en una transaccion: si algo falla no queda una importacion a medias.
 */
public class DataTransferApi {

    private final AppDatabase database;

    DataTransferApi(AppDatabase database) {
        this.database = database;
    }

    /** El respaldo completo como JSON (UTF-8). */
    public Call<byte[]> exportData() {
        return new Call<>(() -> {
            SQLiteDatabase db = database.getReadableDatabase();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                writer.beginObject();
                writer.name("format").value(BackupFileInfo.FORMAT);
                writer.name("version").value(BackupFileInfo.MAX_VERSION);
                writer.name("exportedAt").value(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
                writeProducts(db, writer);
                writePresentations(db, writer);
                writeInventories(db, writer);
                writer.endObject();
            }
            return Response.success(output.toByteArray());
        });
    }

    public Call<ImportResultDTO> importData(byte[] json) {
        return new Call<>(() -> {
            JSONObject root;
            try {
                root = new JSONObject(new String(json, StandardCharsets.UTF_8));
            } catch (JSONException e) {
                return Response.badRequest("El archivo no es un respaldo de inventarios");
            }
            if (!BackupFileInfo.FORMAT.equals(root.optString("format"))) {
                return Response.badRequest("El archivo no es un respaldo de inventarios");
            }
            int version = root.optInt("version", 0);
            if (version < 1 || version > BackupFileInfo.MAX_VERSION) {
                return Response.badRequest("Version de respaldo no soportada: " + version);
            }

            SQLiteDatabase db = database.getWritableDatabase();
            db.beginTransaction();
            try {
                Counters counters = new Counters();
                Map<String, String> descriptionById = importProducts(db, root.optJSONArray("products"), counters);
                importPresentations(db, root.optJSONArray("productPresentations"), descriptionById, counters);
                JSONArray inventories = root.optJSONArray("inventories");
                for (int i = 0; inventories != null && i < inventories.length(); i++) {
                    importInventory(db, inventories.optJSONObject(i), descriptionById, counters);
                }
                db.setTransactionSuccessful();
                return Response.success(counters.toResult());
            } finally {
                db.endTransaction();
            }
        });
    }

    // ------------------------------------------------------------------
    // Exportar
    // ------------------------------------------------------------------

    private static void writeProducts(SQLiteDatabase db, JsonWriter writer) throws IOException {
        writer.name("products").beginArray();
        try (Cursor c = db.rawQuery("SELECT id, description FROM products ORDER BY id", null)) {
            while (c.moveToNext()) {
                writer.beginObject();
                writer.name("id").value(c.getString(0));
                writer.name("description").value(c.getString(1));
                writer.endObject();
            }
        }
        writer.endArray();
    }

    private static void writePresentations(SQLiteDatabase db, JsonWriter writer) throws IOException {
        writer.name("productPresentations").beginArray();
        try (Cursor c = db.rawQuery("SELECT id_product, presentation, barcode FROM product_presentations"
                + " ORDER BY id_product, id", null)) {
            while (c.moveToNext()) {
                writer.beginObject();
                writer.name("productId").value(c.getString(0));
                writer.name("presentation").value(c.getString(1));
                writer.name("barcode").value(c.isNull(2) ? null : c.getString(2));
                writer.endObject();
            }
        }
        writer.endArray();
    }

    private static void writeInventories(SQLiteDatabase db, JsonWriter writer) throws IOException {
        writer.name("inventories").beginArray();
        try (Cursor inventory = db.rawQuery(Queries.inventoryQuery("ORDER BY id"), null)) {
            while (inventory.moveToNext()) {
                InventoryDTO dto = Queries.readInventory(inventory);
                writer.beginObject();
                writer.name("id").value(dto.getId());
                writer.name("date").value(dto.getDate());
                writer.name("presentation").value(dto.getPresentation());
                writer.name("status").value(dto.getStatus());

                writer.name("stock").beginArray();
                try (Cursor c = db.rawQuery("SELECT id_product, stock FROM stock"
                        + " WHERE id_inventory = ? AND id_product IS NOT NULL ORDER BY id", args(dto.getId()))) {
                    while (c.moveToNext()) {
                        writer.beginObject();
                        writer.name("productId").value(c.getString(0));
                        writer.name("stock").value(number(c.getFloat(1)));
                        writer.endObject();
                    }
                }
                writer.endArray();

                writer.name("counts").beginArray();
                try (Cursor c = db.rawQuery("SELECT id_product, quantity, place FROM product_counts"
                        + " WHERE id_inventory = ? ORDER BY id", args(dto.getId()))) {
                    while (c.moveToNext()) {
                        writer.beginObject();
                        writer.name("productId").value(c.getString(0));
                        writer.name("quantity").value(number(c.getFloat(1)));
                        writer.name("place").value(c.getString(2));
                        writer.endObject();
                    }
                }
                writer.endArray();

                writer.endObject();
            }
        }
        writer.endArray();
    }

    /** Float como numero JSON corto ("1.2", no "1.2000000476837158" como lo escribiria un double). */
    private static Number number(float value) {
        return Float.valueOf(value);
    }

    // ------------------------------------------------------------------
    // Importar
    // ------------------------------------------------------------------

    private static final class Counters {
        int productsCreated;
        int productsUpdated;
        int presentationsCreated;
        int barcodesAssigned;
        int barcodeConflicts;
        int inventoriesCreated;
        int stockRowsCreated;
        int countsCreated;
        int rowsSkipped;

        ImportResultDTO toResult() {
            return new ImportResultDTO(productsCreated, productsUpdated, presentationsCreated, barcodesAssigned,
                    barcodeConflicts, inventoriesCreated, stockRowsCreated, countsCreated, rowsSkipped);
        }
    }

    /** Presentacion guardada (o recien creada) durante la importacion. */
    private static final class PresentationRow {
        final long id;
        String barcode;

        PresentationRow(long id, String barcode) {
            this.id = id;
            this.barcode = barcode;
        }
    }

    /** Regresa la descripcion de todos los productos que existen al terminar. */
    private static Map<String, String> importProducts(SQLiteDatabase db, JSONArray products, Counters counters) {
        Map<String, String> descriptionById = new HashMap<>();
        try (Cursor c = db.rawQuery("SELECT id, description FROM products", null)) {
            while (c.moveToNext()) {
                descriptionById.put(c.getString(0), c.getString(1));
            }
        }

        for (int i = 0; products != null && i < products.length(); i++) {
            JSONObject product = products.optJSONObject(i);
            String id = text(product, "id");
            String description = text(product, "description");
            if (id == null || description == null) {
                counters.rowsSkipped++;
                continue;
            }
            ContentValues values = new ContentValues();
            values.put("description", description);
            String existing = descriptionById.get(id);
            if (existing == null) {
                values.put("id", id);
                db.insertOrThrow("products", null, values);
                counters.productsCreated++;
            } else if (!existing.equals(description)) {
                db.update("products", values, "id = ?", new String[]{id});
                counters.productsUpdated++;
            }
            descriptionById.put(id, description);
        }
        return descriptionById;
    }

    private static void importPresentations(SQLiteDatabase db, JSONArray presentations,
                                            Map<String, String> descriptionById, Counters counters) {
        Map<String, PresentationRow> byKey = new HashMap<>();
        Map<String, PresentationRow> byBarcode = new HashMap<>();
        try (Cursor c = db.rawQuery("SELECT id, id_product, presentation, barcode FROM product_presentations", null)) {
            while (c.moveToNext()) {
                ProductPresentation type = ProductPresentation.findByLabel(c.getString(2));
                PresentationRow row = new PresentationRow(c.getLong(0), c.isNull(3) ? null : c.getString(3));
                if (type != null) {
                    byKey.put(key(c.getString(1), type), row);
                }
                if (row.barcode != null) {
                    byBarcode.put(row.barcode, row);
                }
            }
        }

        for (int i = 0; presentations != null && i < presentations.length(); i++) {
            JSONObject item = presentations.optJSONObject(i);
            String productId = text(item, "productId");
            ProductPresentation type = ProductPresentation.findByLabel(text(item, "presentation"));
            if (productId == null || !descriptionById.containsKey(productId) || type == null) {
                counters.rowsSkipped++;
                continue;
            }

            String key = key(productId, type);
            PresentationRow row = byKey.get(key);
            if (row == null) {
                ContentValues values = new ContentValues();
                values.put("id_product", productId);
                values.put("presentation", type.getLabel());
                row = new PresentationRow(db.insertOrThrow("product_presentations", null, values), null);
                byKey.put(key, row);
                counters.presentationsCreated++;
            }

            String barcode = text(item, "barcode");
            if (barcode == null || barcode.equals(row.barcode)) {
                continue;
            }
            PresentationRow owner = byBarcode.get(barcode);
            if (owner != null && owner != row) {
                counters.barcodeConflicts++;
                continue;
            }
            if (row.barcode != null) {
                byBarcode.remove(row.barcode);
            }
            row.barcode = barcode;
            byBarcode.put(barcode, row);
            ContentValues values = new ContentValues();
            values.put("barcode", barcode);
            db.update("product_presentations", values, "id = ?", args(row.id));
            counters.barcodesAssigned++;
        }
    }

    private static void importInventory(SQLiteDatabase db, JSONObject item, Map<String, String> descriptionById,
                                        Counters counters) {
        String presentation = text(item, "presentation");
        String date = validDate(text(item, "date"));
        if (presentation == null || date == null) {
            counters.rowsSkipped++;
            return;
        }

        ContentValues inventory = new ContentValues();
        inventory.put("inventory_date", date);
        inventory.put("presentation", presentation);
        inventory.put("status", importedStatus(text(item, "status")));
        long inventoryId = db.insertOrThrow("inventories", null, inventory);
        counters.inventoriesCreated++;

        JSONArray stock = item.optJSONArray("stock");
        for (int i = 0; stock != null && i < stock.length(); i++) {
            JSONObject row = stock.optJSONObject(i);
            String productId = text(row, "productId");
            Double value = finiteNumber(row, "stock");
            if (productId == null || !descriptionById.containsKey(productId) || value == null) {
                counters.rowsSkipped++;
                continue;
            }
            ContentValues values = new ContentValues();
            values.put("id_product", productId);
            values.put("stock", value);
            values.put("id_inventory", inventoryId);
            db.insertOrThrow("stock", null, values);
            counters.stockRowsCreated++;
        }

        JSONArray counts = item.optJSONArray("counts");
        for (int i = 0; counts != null && i < counts.length(); i++) {
            JSONObject row = counts.optJSONObject(i);
            String productId = text(row, "productId");
            String place = text(row, "place");
            Double quantity = finiteNumber(row, "quantity");
            if (productId == null || !descriptionById.containsKey(productId) || !Queries.isValidPlace(place)
                    || quantity == null) {
                counters.rowsSkipped++;
                continue;
            }
            ContentValues values = new ContentValues();
            values.put("id_inventory", inventoryId);
            values.put("id_product", productId);
            values.put("quantity", quantity);
            values.put("place", place);
            db.insertOrThrow("product_counts", null, values);
            counters.countsCreated++;
        }
    }

    /** LOCKED (bloqueo de un dispositivo del sistema que exporto) se importa como OPENED. */
    private static String importedStatus(String status) {
        if (Queries.STATUS_CLOSED.equals(status) || "PENDING".equals(status)) {
            return status;
        }
        return Queries.STATUS_OPENED;
    }

    /** "yyyy-MM-dd" valido o null. */
    private static String validDate(String date) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        try {
            format.parse(date);
            return date;
        } catch (ParseException e) {
            return null;
        }
    }

    /** Texto recortado; null si falta, es null o queda vacio. */
    private static String text(JSONObject object, String name) {
        if (object == null || !object.has(name) || object.isNull(name)) {
            return null;
        }
        String value = object.optString(name).trim();
        return value.isEmpty() ? null : value;
    }

    private static Double finiteNumber(JSONObject object, String name) {
        if (object == null || !object.has(name) || object.isNull(name)) {
            return null;
        }
        double value = object.optDouble(name);
        return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
    }

    private static String key(String productId, ProductPresentation presentation) {
        return productId + "|" + presentation.name();
    }
}
