package com.example.myapplication.api;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import com.example.myapplication.db.AppDatabase;

import java.util.ArrayList;
import java.util.List;

import static com.example.myapplication.api.Queries.args;

/** Puerto de InventoriesController (sin lock/unlock: la app la usa un solo dispositivo). */
public class InventoriesApi {

    private final AppDatabase database;

    InventoriesApi(AppDatabase database) {
        this.database = database;
    }

    /** Mas recientes primero: por fecha y, entre inventarios del mismo dia, por id (orden de carga). */
    public Call<PageResponse<InventoryDTO>> getInventories(int page, int size) {
        return new Call<>(() -> {
            SQLiteDatabase db = database.getReadableDatabase();
            long total = DatabaseUtils.queryNumEntries(db, "inventories");
            List<InventoryDTO> items = new ArrayList<>();
            try (Cursor c = db.rawQuery(Queries.inventoryQuery("ORDER BY inventory_date DESC, id DESC LIMIT "
                    + size + " OFFSET " + ((long) page * size)), null)) {
                while (c.moveToNext()) {
                    items.add(Queries.readInventory(c));
                }
            }
            return Response.success(new PageResponse<>(items, size, total));
        });
    }

    /** Inventarios cuya presentacion es exactamente "type" ("4 LTS", "PIEZA"...). Lista vacia si no hay. */
    public Call<List<InventoryDTO>> getInventoriesByType(String type) {
        return new Call<>(() -> {
            List<InventoryDTO> items = new ArrayList<>();
            try (Cursor c = database.getReadableDatabase().rawQuery(
                    Queries.inventoryQuery("WHERE presentation = ? ORDER BY inventory_date DESC, id DESC"),
                    new String[]{type})) {
                while (c.moveToNext()) {
                    items.add(Queries.readInventory(c));
                }
            }
            return Response.success(items);
        });
    }

    /** Lo marca como CLOSED para que ya no reciba mas conteos. */
    public Call<InventoryDTO> closeInventory(long id) {
        return new Call<>(() -> {
            SQLiteDatabase db = database.getWritableDatabase();
            InventoryDTO inventory = Queries.findInventory(db, id);
            if (inventory == null) {
                return Response.badRequest("El inventario no existe");
            }
            if (Queries.STATUS_CLOSED.equals(inventory.getStatus())) {
                return Response.badRequest("El inventario ya esta cerrado");
            }
            return Response.success(updateStatus(db, id, Queries.STATUS_CLOSED));
        });
    }

    /** Regresa un inventario cerrado al estado OPENED. */
    public Call<InventoryDTO> reopenInventory(long id) {
        return new Call<>(() -> {
            SQLiteDatabase db = database.getWritableDatabase();
            InventoryDTO inventory = Queries.findInventory(db, id);
            if (inventory == null) {
                return Response.badRequest("El inventario no existe");
            }
            if (!Queries.STATUS_CLOSED.equals(inventory.getStatus())) {
                return Response.badRequest("El inventario no esta cerrado");
            }
            return Response.success(updateStatus(db, id, Queries.STATUS_OPENED));
        });
    }

    /** Borra el inventario con su stock y sus conteos (ON DELETE CASCADE). */
    public Call<Void> deleteInventory(long id) {
        return new Call<>(() -> {
            int deleted = database.getWritableDatabase().delete("inventories", "id = ?", args(id));
            return deleted == 0 ? Response.badRequest("El inventario no existe") : Response.success(null);
        });
    }

    /** Catalogo, conteos (del mas viejo al mas nuevo), stock y presentaciones con codigo de barras. */
    public Call<NormalInventoryDataDTO> getNormalInventoryData(long id) {
        return new Call<>(() -> {
            SQLiteDatabase db = database.getReadableDatabase();
            if (Queries.findInventory(db, id) == null) {
                return Response.badRequest("El inventario no existe");
            }

            List<ProductDTO> products = new ArrayList<>();
            try (Cursor c = db.rawQuery("SELECT id, description FROM products ORDER BY id", null)) {
                while (c.moveToNext()) {
                    products.add(new ProductDTO(c.getString(0), c.getString(1)));
                }
            }

            List<ProductCountEntryDTO> counts = new ArrayList<>();
            try (Cursor c = db.rawQuery("SELECT pc.id, pc.quantity, pc.place, p.id, p.description"
                    + " FROM product_counts pc JOIN products p ON p.id = pc.id_product"
                    + " WHERE pc.id_inventory = ? ORDER BY pc.id", args(id))) {
                while (c.moveToNext()) {
                    counts.add(new ProductCountEntryDTO(c.getLong(0), c.getFloat(1), c.getString(2),
                            new ProductCountEntryDTO.ProductRef(c.getString(3), c.getString(4))));
                }
            }

            List<ProductPresentationDTO> barcodes = new ArrayList<>();
            try (Cursor c = db.rawQuery(Queries.PRESENTATION_SELECT + " WHERE pp.barcode IS NOT NULL", null)) {
                while (c.moveToNext()) {
                    barcodes.add(Queries.readPresentation(c));
                }
            }

            return Response.success(new NormalInventoryDataDTO(products, counts, StockApi.queryStock(db, id), barcodes));
        });
    }

    private static InventoryDTO updateStatus(SQLiteDatabase db, long id, String status) {
        ContentValues values = new ContentValues();
        values.put("status", status);
        db.update("inventories", values, "id = ?", args(id));
        return Queries.findInventory(db, id);
    }
}
