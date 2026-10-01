package com.example.myapplication.api;

/**
 * A diferencia de NewProductCountRequest, da de alta el producto en el
 * catalogo (con "description") y crea el conteo en la misma operacion, para
 * el caso de un producto encontrado fisicamente pero no registrado.
 */
public class CreateProductCountRequest {
    private final long idInventory;
    private final String idProduct;
    private final String description;
    private final float quantity;
    private final String place;

    public CreateProductCountRequest(long idInventory, String idProduct, String description,
                                      float quantity, String place) {
        this.idInventory = idInventory;
        this.idProduct = idProduct;
        this.description = description;
        this.quantity = quantity;
        this.place = place;
    }

    public long getIdInventory() {
        return idInventory;
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
