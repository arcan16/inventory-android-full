package com.example.myapplication.api;

/** Conteo de un producto que ya existe en el catalogo. "place" es obligatorio (SALES_AREA, WAREHOUSE...). */
public class NewProductCountRequest {
    private final long idInventory;
    private final String idProduct;
    private final float quantity;
    private final String place;

    public NewProductCountRequest(long idInventory, String idProduct, float quantity, String place) {
        this.idInventory = idInventory;
        this.idProduct = idProduct;
        this.quantity = quantity;
        this.place = place;
    }

    public long getIdInventory() {
        return idInventory;
    }

    public String getIdProduct() {
        return idProduct;
    }

    public float getQuantity() {
        return quantity;
    }

    public String getPlace() {
        return place;
    }
}
