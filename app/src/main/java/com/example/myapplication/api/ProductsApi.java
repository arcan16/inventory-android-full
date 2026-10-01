package com.example.myapplication.api;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.domain.ProductPresentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.example.myapplication.api.Queries.args;

/** Puerto de ProductsController. */
public class ProductsApi {

    private final AppDatabase database;

    ProductsApi(AppDatabase database) {
        this.database = database;
    }

    /**
     * Catalogo paginado y ordenado por descripcion. Si "query" trae texto, filtra
     * por coincidencia parcial (sin distinguir mayusculas) en el id o la descripcion.
     */
    public Call<PageResponse<ProductDTO>> getProducts(int page, int size, String query) {
        return new Call<>(() -> {
            SQLiteDatabase db = database.getReadableDatabase();
            String where = "";
            String[] whereArgs = null;
            if (!Queries.isBlank(query)) {
                // % y _ son comodines de LIKE: se escapan para buscar el texto tal cual.
                String pattern = "%" + query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                where = " WHERE id LIKE ? ESCAPE '\\' OR description LIKE ? ESCAPE '\\'";
                whereArgs = new String[]{pattern, pattern};
            }

            long total;
            try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM products" + where, whereArgs)) {
                total = c.moveToFirst() ? c.getLong(0) : 0;
            }

            List<ProductDTO> items = new ArrayList<>();
            try (Cursor c = db.rawQuery("SELECT id, description FROM products" + where
                    + " ORDER BY description COLLATE NOCASE, id LIMIT " + size + " OFFSET " + ((long) page * size), whereArgs)) {
                while (c.moveToNext()) {
                    items.add(new ProductDTO(c.getString(0), c.getString(1)));
                }
            }
            return Response.success(new PageResponse<>(items, size, total));
        });
    }

    /**
     * Actualiza la descripcion. El id (codigo del producto) no se puede cambiar:
     * romperia las referencias en stock y en los conteos ya registrados.
     */
    public Call<ProductDTO> updateProduct(String productId, UpdateProductRequest request) {
        return new Call<>(() -> {
            if (Queries.isBlank(request.getDescription())) {
                return Response.badRequest("La descripcion es obligatoria");
            }
            String description = request.getDescription().trim();
            ContentValues values = new ContentValues();
            values.put("description", description);
            int updated = database.getWritableDatabase().update("products", values, "id = ?", new String[]{productId});
            if (updated == 0) {
                return Response.badRequest("El producto no existe");
            }
            return Response.success(new ProductDTO(productId, description));
        });
    }

    /** Presentaciones del producto (con su codigo de barras), de menor a mayor volumen. */
    public Call<List<ProductPresentationDTO>> getPresentations(String productId) {
        return new Call<>(() -> {
            SQLiteDatabase db = database.getReadableDatabase();
            if (Queries.findProductDescription(db, productId) == null) {
                return Response.badRequest("El producto no existe");
            }
            List<ProductPresentationDTO> items = new ArrayList<>();
            try (Cursor c = db.rawQuery(Queries.PRESENTATION_SELECT + " WHERE pp.id_product = ?", new String[]{productId})) {
                while (c.moveToNext()) {
                    items.add(Queries.readPresentation(c));
                }
            }
            Collections.sort(items, Comparator.comparingInt(p -> presentationOrder(p.getPresentation())));
            return Response.success(items);
        });
    }

    /** 404 si ninguna presentacion tiene ese codigo. */
    public Call<ProductPresentationDTO> getPresentationByBarcode(String barcode) {
        return new Call<>(() -> {
            ProductPresentationDTO presentation = Queries.findPresentationByBarcode(database.getReadableDatabase(), barcode.trim());
            return presentation != null ? Response.success(presentation) : Response.notFound("El codigo no esta registrado");
        });
    }

    /**
     * Asigna un codigo de barras a la presentacion indicada del producto (desde
     * el conteo, al leer un codigo no registrado). Si el producto aun no tiene
     * esa presentacion, se crea. No reemplaza un codigo distinto ya guardado ni
     * toma un codigo que pertenece a otra presentacion.
     */
    public Call<ProductPresentationDTO> assignBarcode(String productId, AssignBarcodeRequest request) {
        return new Call<>(() -> {
            if (Queries.isBlank(request.getBarcode()) || Queries.isBlank(request.getPresentation())) {
                return Response.badRequest("La presentacion y el codigo de barras son obligatorios");
            }
            SQLiteDatabase db = database.getWritableDatabase();
            if (Queries.findProductDescription(db, productId) == null) {
                return Response.badRequest("El producto no existe");
            }
            ProductPresentation type = ProductPresentation.findByLabel(request.getPresentation());
            if (type == null) {
                return Response.badRequest("Presentacion desconocida: " + request.getPresentation());
            }

            String barcode = request.getBarcode().trim();
            ProductPresentationDTO owner = Queries.findPresentationByBarcode(db, barcode);
            ProductPresentationDTO presentation = findPresentation(db, productId, type);

            if (owner != null && (presentation == null || !owner.getId().equals(presentation.getId()))) {
                return Response.badRequest("El codigo de barras ya esta asignado a " + Queries.describeOwner(owner));
            }

            long presentationId;
            if (presentation == null) {
                ContentValues values = new ContentValues();
                values.put("id_product", productId);
                values.put("presentation", type.getLabel());
                values.put("barcode", barcode);
                presentationId = db.insertOrThrow("product_presentations", null, values);
            } else if (presentation.getBarcode() != null && !presentation.getBarcode().equals(barcode)) {
                return Response.badRequest(productId + " en " + type.getLabel() + " ya tiene el codigo " + presentation.getBarcode());
            } else {
                presentationId = presentation.getId();
                ContentValues values = new ContentValues();
                values.put("barcode", barcode);
                db.update("product_presentations", values, "id = ?", args(presentationId));
            }
            return Response.success(Queries.findPresentationById(db, presentationId));
        });
    }

    /**
     * Edita, desde una presentacion concreta, la descripcion del producto y el
     * codigo de barras de esa presentacion en una sola transaccion. Un barcode
     * vacio conserva el codigo guardado, salvo que clearBarcode sea true.
     */
    public Call<ProductPresentationDTO> updatePresentation(long presentationId, String productId,
                                                           UpdateProductPresentationRequest request) {
        return new Call<>(() -> {
            if (Queries.isBlank(request.getDescription())) {
                return Response.badRequest("La descripcion es obligatoria");
            }
            SQLiteDatabase db = database.getWritableDatabase();
            ProductPresentationDTO presentation = Queries.findPresentationById(db, presentationId);
            if (presentation == null || !presentation.getProductId().equals(productId)) {
                return Response.badRequest("La presentacion no existe para este producto");
            }

            String barcode = Queries.isBlank(request.getBarcode()) ? null : request.getBarcode().trim();
            if (barcode != null) {
                ProductPresentationDTO owner = Queries.findPresentationByBarcode(db, barcode);
                if (owner != null && owner.getId() != presentationId) {
                    return Response.badRequest("El codigo de barras ya esta asignado a " + Queries.describeOwner(owner));
                }
            }

            db.beginTransaction();
            try {
                ContentValues product = new ContentValues();
                product.put("description", request.getDescription().trim());
                db.update("products", product, "id = ?", new String[]{productId});

                if (barcode != null || request.isClearBarcode()) {
                    ContentValues values = new ContentValues();
                    if (barcode != null) {
                        values.put("barcode", barcode);
                    } else {
                        values.putNull("barcode");
                    }
                    db.update("product_presentations", values, "id = ?", args(presentationId));
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return Response.success(Queries.findPresentationById(db, presentationId));
        });
    }

    private static ProductPresentationDTO findPresentation(SQLiteDatabase db, String productId, ProductPresentation type) {
        try (Cursor c = db.rawQuery(Queries.PRESENTATION_SELECT + " WHERE pp.id_product = ? AND pp.presentation = ?",
                new String[]{productId, type.getLabel()})) {
            return c.moveToFirst() ? Queries.readPresentation(c) : null;
        }
    }

    /** Orden del enum ProductPresentation; un label desconocido va al final. */
    private static int presentationOrder(String label) {
        ProductPresentation presentation = ProductPresentation.findByLabel(label);
        return presentation != null ? presentation.ordinal() : Integer.MAX_VALUE;
    }
}
