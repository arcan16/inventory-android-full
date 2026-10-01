package com.example.myapplication.api;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.myapplication.db.AppDatabase;

import java.util.ArrayList;
import java.util.List;

import static com.example.myapplication.api.Queries.args;

/** Puerto de StockController. */
public class StockApi {

    private final AppDatabase database;

    StockApi(AppDatabase database) {
        this.database = database;
    }

    /** Existencias esperadas del inventario, en el orden del archivo cargado. */
    public Call<List<StockItemDTO>> getStock(long idInventory) {
        return new Call<>(() -> Response.success(queryStock(database.getReadableDatabase(), idInventory)));
    }

    static List<StockItemDTO> queryStock(SQLiteDatabase db, long idInventory) {
        List<StockItemDTO> items = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT s.id, s.id_product, p.description, s.stock, s.id_inventory"
                + " FROM stock s JOIN products p ON p.id = s.id_product"
                + " WHERE s.id_inventory = ? ORDER BY s.id", args(idInventory))) {
            while (c.moveToNext()) {
                items.add(new StockItemDTO(c.getLong(0), c.getString(1), c.getString(2), c.getFloat(3), c.getLong(4)));
            }
        }
        return items;
    }
}
