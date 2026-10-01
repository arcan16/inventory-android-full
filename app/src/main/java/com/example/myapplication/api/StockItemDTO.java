package com.example.myapplication.api;

/** Existencia esperada de un producto en el inventario (un renglon del CSV cargado). */
public class StockItemDTO {
    private final long id;
    private final String idProduct;
    private final String description;
    private final float stock;
    private final long idInventory;

    public StockItemDTO(long id, String idProduct, String description, float stock, long idInventory) {
        this.id = id;
        this.idProduct = idProduct;
        this.description = description;
        this.stock = stock;
        this.idInventory = idInventory;
    }

    public String getIdProduct() {
        return idProduct;
    }

    public String getDescription() {
        return description;
    }

    public float getStock() {
        return stock;
    }
}
