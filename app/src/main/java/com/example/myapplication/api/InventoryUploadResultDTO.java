package com.example.myapplication.api;

/**
 * Resultado de cargar el CSV de existencias. presentationsSkipped: filas cuya
 * UNIDAD no es una presentacion conocida; barcodeConflicts: codigos de barras
 * que ya pertenecen a otra presentacion y por eso no se asignaron.
 */
public class InventoryUploadResultDTO {
    private final long inventoryId;
    private final String presentation;
    private final int rowsProcessed;
    private final int productsCreated;
    private final int stockRowsCreated;
    private final int presentationsCreated;
    private final int barcodesAssigned;
    private final int presentationsSkipped;
    private final int barcodeConflicts;

    public InventoryUploadResultDTO(long inventoryId, String presentation, int rowsProcessed,
                                    int productsCreated, int stockRowsCreated, int presentationsCreated,
                                    int barcodesAssigned, int presentationsSkipped, int barcodeConflicts) {
        this.inventoryId = inventoryId;
        this.presentation = presentation;
        this.rowsProcessed = rowsProcessed;
        this.productsCreated = productsCreated;
        this.stockRowsCreated = stockRowsCreated;
        this.presentationsCreated = presentationsCreated;
        this.barcodesAssigned = barcodesAssigned;
        this.presentationsSkipped = presentationsSkipped;
        this.barcodeConflicts = barcodeConflicts;
    }

    public long getInventoryId() {
        return inventoryId;
    }

    public String getPresentation() {
        return presentation;
    }

    public int getRowsProcessed() {
        return rowsProcessed;
    }

    public int getProductsCreated() {
        return productsCreated;
    }

    public int getStockRowsCreated() {
        return stockRowsCreated;
    }

    public int getPresentationsCreated() {
        return presentationsCreated;
    }

    public int getBarcodesAssigned() {
        return barcodesAssigned;
    }

    public int getPresentationsSkipped() {
        return presentationsSkipped;
    }

    public int getBarcodeConflicts() {
        return barcodeConflicts;
    }
}
