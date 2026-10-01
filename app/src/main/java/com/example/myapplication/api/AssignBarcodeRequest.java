package com.example.myapplication.api;

/**
 * Asigna un codigo de barras a una presentacion ("1 LT", "4 LTS"...) de un
 * producto, creandola si el producto aun no la tiene.
 */
public class AssignBarcodeRequest {
    private final String presentation;
    private final String barcode;

    public AssignBarcodeRequest(String presentation, String barcode) {
        this.presentation = presentation;
        this.barcode = barcode;
    }

    public String getPresentation() {
        return presentation;
    }

    public String getBarcode() {
        return barcode;
    }
}
