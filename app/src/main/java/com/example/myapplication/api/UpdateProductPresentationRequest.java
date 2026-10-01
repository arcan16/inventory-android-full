package com.example.myapplication.api;

/**
 * Actualiza la descripcion del producto y el codigo de barras de una de sus
 * presentaciones. Un barcode vacio conserva el codigo guardado, salvo que
 * clearBarcode sea true (boton "Quitar codigo").
 */
public class UpdateProductPresentationRequest {
    private final String description;
    private final String barcode;
    private final boolean clearBarcode;

    public UpdateProductPresentationRequest(String description, String barcode, boolean clearBarcode) {
        this.description = description;
        this.barcode = barcode;
        this.clearBarcode = clearBarcode;
    }

    public String getDescription() {
        return description;
    }

    public String getBarcode() {
        return barcode;
    }

    public boolean isClearBarcode() {
        return clearBarcode;
    }
}
