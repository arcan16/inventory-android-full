package com.example.myapplication.api;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Consultas y conversiones que comparten varias Api. */
final class Queries {

    static final String STATUS_OPENED = "OPENED";
    static final String STATUS_CLOSED = "CLOSED";

    private static final String INVENTORY_COLUMNS = "id, inventory_date, presentation, status";

    /** Seleccion de una presentacion con la descripcion de su producto (ver readPresentation). */
    static final String PRESENTATION_SELECT =
            "SELECT pp.id, pp.id_product, p.description, pp.presentation, pp.barcode"
                    + " FROM product_presentations pp JOIN products p ON p.id = pp.id_product";

    private Queries() {
    }

    static String[] args(Object... values) {
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = String.valueOf(values[i]);
        }
        return result;
    }

    /** Fecha local de hoy en el formato guardado en inventories.inventory_date. */
    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** Las cantidades eran decimal(6,3) en MySQL; los totales se redondean igual para evitar 0.30000000000000004. */
    static double round3(double value) {
        return Math.round(value * 1000d) / 1000d;
    }

    /** Lugares validos de un conteo (enum ProductLocation del backend). */
    static boolean isValidPlace(String place) {
        return "SALES_AREA".equals(place) || "WAREHOUSE".equals(place)
                || "STORAGE_AREA".equals(place) || "NOTE".equals(place);
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** null si no existe. */
    static InventoryDTO findInventory(SQLiteDatabase db, long id) {
        try (Cursor c = db.rawQuery("SELECT " + INVENTORY_COLUMNS + " FROM inventories WHERE id = ?", args(id))) {
            return c.moveToFirst() ? readInventory(c) : null;
        }
    }

    static String inventoryQuery(String whereAndOrder) {
        return "SELECT " + INVENTORY_COLUMNS + " FROM inventories " + whereAndOrder;
    }

    static InventoryDTO readInventory(Cursor c) {
        return new InventoryDTO(c.getLong(0), c.getString(1), c.getString(2), c.getString(3));
    }

    /** Descripcion del producto; null si no existe. */
    static String findProductDescription(SQLiteDatabase db, String productId) {
        if (productId == null) {
            return null;
        }
        try (Cursor c = db.rawQuery("SELECT description FROM products WHERE id = ?", new String[]{productId})) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    static ProductPresentationDTO readPresentation(Cursor c) {
        return new ProductPresentationDTO(c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                c.isNull(4) ? null : c.getString(4));
    }

    /** Presentacion que tiene ese codigo de barras; null si ninguna. */
    static ProductPresentationDTO findPresentationByBarcode(SQLiteDatabase db, String barcode) {
        try (Cursor c = db.rawQuery(PRESENTATION_SELECT + " WHERE pp.barcode = ?", new String[]{barcode})) {
            return c.moveToFirst() ? readPresentation(c) : null;
        }
    }

    static ProductPresentationDTO findPresentationById(SQLiteDatabase db, long presentationId) {
        try (Cursor c = db.rawQuery(PRESENTATION_SELECT + " WHERE pp.id = ?", args(presentationId))) {
            return c.moveToFirst() ? readPresentation(c) : null;
        }
    }

    /** Mismo formato que usaba el backend en sus mensajes: "860/P5 (4 LTS)". */
    static String describeOwner(ProductPresentationDTO owner) {
        return owner.getProductId() + " (" + owner.getPresentation() + ")";
    }
}
