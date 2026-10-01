package com.example.myapplication.api;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.report.CountReportPdf;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.example.myapplication.api.Queries.args;

/** Puerto de ProductCountsController. */
public class ProductCountsApi {

    private final AppDatabase database;
    private final File reportsDir;

    ProductCountsApi(AppDatabase database, File reportsDir) {
        this.database = database;
        this.reportsDir = reportsDir;
    }

    /** Registra un producto (ya existente en el catalogo) encontrado en el conteo fisico. */
    public Call<ProductCountCreatedDTO> addProductCount(NewProductCountRequest request) {
        return new Call<>(() -> {
            if (!Queries.isValidPlace(request.getPlace())) {
                return Response.badRequest("Lugar del conteo invalido: " + request.getPlace());
            }
            SQLiteDatabase db = database.getWritableDatabase();
            if (Queries.findInventory(db, request.getIdInventory()) == null) {
                return Response.badRequest("El id del Inventario no existe");
            }
            String description = Queries.findProductDescription(db, request.getIdProduct());
            if (description == null) {
                return Response.badRequest("El id del Producto no existe");
            }

            long id = insertCount(db, request.getIdInventory(), request.getIdProduct(), request.getQuantity(), request.getPlace());
            return Response.success(new ProductCountCreatedDTO(id, request.getIdProduct(), description,
                    request.getQuantity(), request.getPlace()));
        });
    }

    /** Da de alta un producto que no estaba en el catalogo y registra su conteo, todo o nada. */
    public Call<CreateProductCountResultDTO> createProductAddCount(CreateProductCountRequest request) {
        return new Call<>(() -> {
            if (Queries.isBlank(request.getIdProduct()) || Queries.isBlank(request.getDescription())) {
                return Response.badRequest("El id y la descripcion del producto son obligatorios");
            }
            if (!Queries.isValidPlace(request.getPlace())) {
                return Response.badRequest("Lugar del conteo invalido: " + request.getPlace());
            }
            SQLiteDatabase db = database.getWritableDatabase();
            if (Queries.findInventory(db, request.getIdInventory()) == null) {
                return Response.badRequest("El id del inventario no existe, favor de verificarlo");
            }
            if (Queries.findProductDescription(db, request.getIdProduct()) != null) {
                return Response.badRequest("El id del producto ya existe, no es posible duplicarlo");
            }

            long countId;
            db.beginTransaction();
            try {
                ContentValues product = new ContentValues();
                product.put("id", request.getIdProduct());
                product.put("description", request.getDescription());
                db.insertOrThrow("products", null, product);
                countId = insertCount(db, request.getIdInventory(), request.getIdProduct(), request.getQuantity(), request.getPlace());
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            return Response.success(new CreateProductCountResultDTO(
                    new CreateProductCountResultDTO.NewProduct(request.getIdProduct(), request.getDescription()),
                    new CreateProductCountResultDTO.CountCreated(countId, request.getQuantity(), request.getPlace())));
        });
    }

    public Call<Void> deleteProductCount(long id) {
        return new Call<>(() -> {
            int deleted = database.getWritableDatabase().delete("product_counts", "id = ?", args(id));
            return deleted == 0 ? Response.badRequest("El conteo no existe") : Response.success(null);
        });
    }

    /**
     * Resumen del conteo: por producto, lo contado, el stock esperado y la
     * diferencia. onlyDifferences=true devuelve solo los renglones que no
     * coinciden. 400 si el inventario aun no tiene stock o conteos.
     */
    public Call<PageResponse<CountsDifferenceDTO>> getSummary(long idInventory, int page, int size, boolean onlyDifferences) {
        return new Call<>(() -> {
            SQLiteDatabase db = database.getReadableDatabase();
            if (!hasStockAndCounts(db, idInventory)) {
                return Response.badRequest("El inventario aun no tiene stock o conteos");
            }
            List<CountsDifferenceDTO> rows = buildCountDifference(db, idInventory);
            if (onlyDifferences) {
                List<CountsDifferenceDTO> filtered = new ArrayList<>();
                for (CountsDifferenceDTO row : rows) {
                    if (hasDifference(row)) {
                        filtered.add(row);
                    }
                }
                rows = filtered;
            }

            int start = (int) Math.min((long) page * size, rows.size());
            int end = Math.min(start + size, rows.size());
            return Response.success(new PageResponse<>(new ArrayList<>(rows.subList(start, end)), size, rows.size()));
        });
    }

    /** Genera el reporte PDF del conteo en el directorio de reportes y regresa el archivo. */
    public Call<File> downloadReport(long idInventory) {
        return new Call<>(() -> {
            SQLiteDatabase db = database.getReadableDatabase();
            if (!hasStockAndCounts(db, idInventory)) {
                return Response.badRequest("El inventario aun no tiene stock o conteos");
            }
            List<CountsDifferenceDTO> report = buildCountDifference(db, idInventory);
            InventoryDTO inventory = Queries.findInventory(db, idInventory);

            if (!reportsDir.exists() && !reportsDir.mkdirs()) {
                throw new IOException("No se pudo crear el directorio de reportes");
            }
            File pdfFile = new File(reportsDir, "summary_" + idInventory + ".pdf");
            CountReportPdf.write(pdfFile, idInventory, inventory, report);
            return Response.success(pdfFile);
        });
    }

    /** Inventarios que ya tienen conteos (de los que se puede generar reporte). */
    public Call<List<ReportDTO>> getAllReports() {
        return new Call<>(() -> {
            List<ReportDTO> reports = new ArrayList<>();
            try (Cursor c = database.getReadableDatabase().rawQuery("SELECT i.id, i.inventory_date, i.presentation"
                    + " FROM inventories i"
                    + " WHERE EXISTS (SELECT 1 FROM product_counts pc WHERE pc.id_inventory = i.id)"
                    + " ORDER BY i.inventory_date DESC, i.id DESC", null)) {
                while (c.moveToNext()) {
                    reports.add(new ReportDTO(c.getLong(0), c.getString(1), c.getString(2)));
                }
            }
            return Response.success(reports);
        });
    }

    private static long insertCount(SQLiteDatabase db, long idInventory, String idProduct, float quantity, String place) {
        ContentValues values = new ContentValues();
        values.put("id_inventory", idInventory);
        values.put("id_product", idProduct);
        values.put("quantity", quantity);
        values.put("place", place);
        return db.insertOrThrow("product_counts", null, values);
    }

    private static boolean hasStockAndCounts(SQLiteDatabase db, long idInventory) {
        try (Cursor c = db.rawQuery("SELECT EXISTS (SELECT 1 FROM stock WHERE id_inventory = ?)"
                + " AND EXISTS (SELECT 1 FROM product_counts WHERE id_inventory = ?)", args(idInventory, idInventory))) {
            return c.moveToFirst() && c.getInt(0) == 1;
        }
    }

    /**
     * Stock del inventario con lo contado de cada producto (en el orden del
     * archivo cargado), seguido de los productos contados que no venian en el
     * stock: su stock es 0 y toda su cantidad contada es diferencia.
     */
    static List<CountsDifferenceDTO> buildCountDifference(SQLiteDatabase db, long idInventory) {
        List<CountsDifferenceDTO> rows = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT s.id, p.description, s.id_product, s.stock, COALESCE(SUM(pc.quantity), 0)"
                + " FROM stock s"
                + " JOIN products p ON p.id = s.id_product"
                + " LEFT JOIN product_counts pc ON pc.id_product = s.id_product AND pc.id_inventory = s.id_inventory"
                + " WHERE s.id_inventory = ?"
                + " GROUP BY s.id ORDER BY s.id", args(idInventory))) {
            while (c.moveToNext()) {
                float stock = c.getFloat(3);
                double sum = Queries.round3(c.getDouble(4));
                rows.add(new CountsDifferenceDTO(c.getLong(0), c.getString(1), c.getString(2), stock, sum,
                        Queries.round3(sum - stock)));
            }
        }

        try (Cursor c = db.rawQuery("SELECT p.id, p.description, SUM(pc.quantity)"
                + " FROM product_counts pc JOIN products p ON p.id = pc.id_product"
                + " WHERE pc.id_inventory = ?"
                + " AND NOT EXISTS (SELECT 1 FROM stock s WHERE s.id_inventory = pc.id_inventory AND s.id_product = p.id)"
                + " GROUP BY p.id, p.description ORDER BY p.id", args(idInventory))) {
            while (c.moveToNext()) {
                double sum = Queries.round3(c.getDouble(2));
                rows.add(new CountsDifferenceDTO(0, c.getString(1), c.getString(0), 0f, sum, sum));
            }
        }
        return rows;
    }

    /** Diferencia distinta de cero; la tolerancia absorbe el redondeo de los decimales de 3 posiciones. */
    static boolean hasDifference(CountsDifferenceDTO row) {
        return Math.abs(row.getDifference()) >= 0.0005;
    }
}
