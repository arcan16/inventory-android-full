package com.example.myapplication.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

/**
 * Base de datos local que reemplaza a la de MySQL del backend. El esquema es
 * el resultado de las migraciones V1-V5 de inventory-comex, sin la tabla de
 * usuarios ni las columnas de bloqueo (created_by, locked_by, locked_at,
 * locked_device): la app la usa una sola persona en un solo dispositivo.
 *
 * Las fechas se guardan como texto "yyyy-MM-dd" (el mismo formato que
 * devolvia el backend en el JSON) y las cantidades como REAL; los totales se
 * redondean a 3 decimales al leerlos, como la columna decimal(6,3) original.
 *
 * Al subir el esquema, agregar un "case" en onUpgrade por cada version (como
 * se hacia con un archivo de migracion de Flyway) en lugar de recrear tablas.
 */
public final class AppDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "inventories.db";
    private static final int DATABASE_VERSION = 1;

    private static volatile AppDatabase instance;

    /** name null = base de datos en memoria. */
    private AppDatabase(Context context, String name) {
        super(context, name, null, DATABASE_VERSION);
    }

    /** Base de datos vacia en memoria, independiente de la de la app; para pruebas. */
    @VisibleForTesting
    public static AppDatabase createInMemory(@NonNull Context context) {
        return new AppDatabase(context, null);
    }

    /** Se llama una vez desde InventoryApp.onCreate. */
    public static void init(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = new AppDatabase(context.getApplicationContext(), DATABASE_NAME);
                }
            }
        }
    }

    @NonNull
    public static AppDatabase get() {
        AppDatabase db = instance;
        if (db == null) {
            throw new IllegalStateException("AppDatabase.init() no se ha llamado");
        }
        return db;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        // SQLite no valida llaves foraneas (ni ON DELETE CASCADE) a menos que se active.
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE inventories ("
                + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " inventory_date TEXT NOT NULL,"
                + " presentation TEXT NOT NULL,"
                // OPENED: creado o en conteo, PENDING: comenzado sin finalizar, CLOSED: finalizado.
                + " status TEXT NOT NULL DEFAULT 'OPENED' CHECK (status IN ('OPENED','PENDING','CLOSED'))"
                + ")");

        db.execSQL("CREATE TABLE products ("
                + " id TEXT NOT NULL PRIMARY KEY,"
                + " description TEXT NOT NULL"
                + ")");

        db.execSQL("CREATE TABLE stock ("
                + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " id_product TEXT REFERENCES products (id) ON DELETE CASCADE,"
                + " stock REAL NOT NULL,"
                + " id_inventory INTEGER NOT NULL REFERENCES inventories (id) ON DELETE CASCADE"
                + ")");
        db.execSQL("CREATE INDEX idx_stock_inventory ON stock (id_inventory)");
        db.execSQL("CREATE INDEX idx_stock_product ON stock (id_product)");

        db.execSQL("CREATE TABLE product_counts ("
                + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " id_inventory INTEGER NOT NULL REFERENCES inventories (id) ON DELETE CASCADE,"
                + " id_product TEXT NOT NULL REFERENCES products (id),"
                + " quantity REAL NOT NULL,"
                // Lugar en que se encontro el producto (piso, almacen, bodega o nota).
                + " place TEXT NOT NULL CHECK (place IN ('SALES_AREA','WAREHOUSE','STORAGE_AREA','NOTE'))"
                + ")");
        db.execSQL("CREATE INDEX idx_counts_inventory ON product_counts (id_inventory)");
        db.execSQL("CREATE INDEX idx_counts_product ON product_counts (id_product)");

        // El codigo de barras pertenece a cada presentacion del producto, no al producto.
        db.execSQL("CREATE TABLE product_presentations ("
                + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " id_product TEXT NOT NULL REFERENCES products (id) ON DELETE CASCADE,"
                + " presentation TEXT NOT NULL,"
                + " barcode TEXT UNIQUE,"
                + " UNIQUE (id_product, presentation)"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 es la primera; aun no hay migraciones.
    }
}
