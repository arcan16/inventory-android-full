package com.example.myapplication.api;

/**
 * Solo la descripcion del producto. Los codigos de barras se editan por
 * presentacion con UpdateProductPresentationRequest.
 */
public class UpdateProductRequest {
    private final String description;

    public UpdateProductRequest(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
