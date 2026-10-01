package com.example.myapplication.api;

/**
 * Resultado de importar un respaldo (mismos campos que ImportResultDTO del
 * backend). rowsSkipped: renglones que no se pudieron importar; barcodeConflicts:
 * codigos de barras que ya pertenecen a otra presentacion.
 */
public class ImportResultDTO {
    final int productsCreated;
    final int productsUpdated;
    final int presentationsCreated;
    final int barcodesAssigned;
    final int barcodeConflicts;
    final int inventoriesCreated;
    final int stockRowsCreated;
    final int countsCreated;
    final int rowsSkipped;

    ImportResultDTO(int productsCreated, int productsUpdated, int presentationsCreated, int barcodesAssigned,
                    int barcodeConflicts, int inventoriesCreated, int stockRowsCreated, int countsCreated,
                    int rowsSkipped) {
        this.productsCreated = productsCreated;
        this.productsUpdated = productsUpdated;
        this.presentationsCreated = presentationsCreated;
        this.barcodesAssigned = barcodesAssigned;
        this.barcodeConflicts = barcodeConflicts;
        this.inventoriesCreated = inventoriesCreated;
        this.stockRowsCreated = stockRowsCreated;
        this.countsCreated = countsCreated;
        this.rowsSkipped = rowsSkipped;
    }

    public int getProductsCreated() {
        return productsCreated;
    }

    public int getProductsUpdated() {
        return productsUpdated;
    }

    public int getPresentationsCreated() {
        return presentationsCreated;
    }

    public int getBarcodesAssigned() {
        return barcodesAssigned;
    }

    public int getBarcodeConflicts() {
        return barcodeConflicts;
    }

    public int getInventoriesCreated() {
        return inventoriesCreated;
    }

    public int getStockRowsCreated() {
        return stockRowsCreated;
    }

    public int getCountsCreated() {
        return countsCreated;
    }

    public int getRowsSkipped() {
        return rowsSkipped;
    }
}
