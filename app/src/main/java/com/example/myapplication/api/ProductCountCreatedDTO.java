package com.example.myapplication.api;

/** Conteo recien registrado (ProductCountsApi.addProductCount). */
public class ProductCountCreatedDTO {
    private final long id;
    private final String idProduct;
    private final String description;
    private final float quantity;
    private final String place;

    public ProductCountCreatedDTO(long id, String idProduct, String description, float quantity, String place) {
        this.id = id;
        this.idProduct = idProduct;
        this.description = description;
        this.quantity = quantity;
        this.place = place;
    }

    public long getId() {
        return id;
    }

    public String getIdProduct() {
        return idProduct;
    }

    public String getDescription() {
        return description;
    }

    public float getQuantity() {
        return quantity;
    }

    public String getPlace() {
        return place;
    }
}
