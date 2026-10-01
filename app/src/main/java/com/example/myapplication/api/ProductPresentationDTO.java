package com.example.myapplication.api;

/**
 * Una presentacion ("1 LT", "4 LTS"...) de un producto con su codigo de barras
 * y la descripcion del producto.
 */
public class ProductPresentationDTO {
    private final Long id;
    private final String productId;
    private final String description;
    private final String presentation;
    private final String barcode;

    public ProductPresentationDTO(Long id, String productId, String description, String presentation, String barcode) {
        this.id = id;
        this.productId = productId;
        this.description = description;
        this.presentation = presentation;
        this.barcode = barcode;
    }

    public Long getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public String getDescription() {
        return description;
    }

    public String getPresentation() {
        return presentation;
    }

    public String getBarcode() {
        return barcode;
    }
}
