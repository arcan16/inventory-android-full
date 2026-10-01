package com.example.myapplication.api;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.myapplication.db.AppDatabase;

import java.io.File;

/**
 * Punto de acceso a las operaciones de la app. Antes construia clientes
 * Retrofit hacia el backend; ahora cada Api ejecuta la misma logica que su
 * controller del backend contra la base de datos local (ver AppDatabase).
 */
public final class ApiClient {

    private static File reportsDir;

    private ApiClient() {
    }

    /** Se llama una vez desde InventoryApp.onCreate, despues de AppDatabase.init. */
    public static void init(@NonNull Context context) {
        // Mismo directorio que comparte el FileProvider (res/xml/file_paths.xml) para abrir los PDF.
        reportsDir = new File(context.getApplicationContext().getCacheDir(), "reports");
    }

    public static InventoriesApi createInventoriesApi() {
        return new InventoriesApi(AppDatabase.get());
    }

    public static InventoryUploadApi createInventoryUploadApi() {
        return new InventoryUploadApi(AppDatabase.get());
    }

    public static ProductCountsApi createProductCountsApi() {
        return new ProductCountsApi(AppDatabase.get(), reportsDir);
    }

    public static ProductsApi createProductsApi() {
        return new ProductsApi(AppDatabase.get());
    }

    public static StockApi createStockApi() {
        return new StockApi(AppDatabase.get());
    }

    public static DataTransferApi createDataTransferApi() {
        return new DataTransferApi(AppDatabase.get());
    }
}
